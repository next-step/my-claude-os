// GET/PUT /api/me/preferences — 실 DB(Prisma). 계약: UserPreference (OS.md 12.5).
// M1 단일 로컬 사용자 → UserPreference 고정 1행(id="local").
// roles/locations/keywords 는 SQLite 배열 미지원으로 JSON 문자열 저장 → 경계에서 parse/stringify.
import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/db";
import { LOCAL_USER_ID } from "@/types/contract";
import type { ExperienceLevel, UserPreference } from "@/types/contract";

const DEFAULT_PREF: UserPreference = {
  roles: [],
  locations: [],
  experience: "ANY",
  keywords: [],
};

const VALID_EXPERIENCE: ExperienceLevel[] = ["NEW", "EXPERIENCED", "ANY"];

/** JSON 문자열 → string[] (파싱 실패 시 빈 배열) */
function parseStrArray(v: string): string[] {
  try {
    const parsed = JSON.parse(v);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

/** 입력값을 string[] 로 정규화 */
function asStrArray(v: unknown): string[] {
  return Array.isArray(v) ? v.map(String) : [];
}

export async function GET() {
  const row = await prisma.userPreference.findUnique({
    where: { id: LOCAL_USER_ID },
  });
  if (!row) return NextResponse.json(DEFAULT_PREF);

  const pref: UserPreference = {
    roles: parseStrArray(row.roles),
    locations: parseStrArray(row.locations),
    experience: (VALID_EXPERIENCE.includes(row.experience as ExperienceLevel)
      ? row.experience
      : "ANY") as ExperienceLevel,
    keywords: parseStrArray(row.keywords),
  };
  return NextResponse.json(pref);
}

export async function PUT(req: NextRequest) {
  const body = (await req.json()) as Partial<UserPreference>;

  const experience: ExperienceLevel =
    body.experience && VALID_EXPERIENCE.includes(body.experience)
      ? body.experience
      : "ANY";
  const pref: UserPreference = {
    roles: asStrArray(body.roles),
    locations: asStrArray(body.locations),
    experience,
    keywords: asStrArray(body.keywords),
  };

  const data = {
    roles: JSON.stringify(pref.roles),
    locations: JSON.stringify(pref.locations),
    experience: pref.experience,
    keywords: JSON.stringify(pref.keywords),
  };

  await prisma.userPreference.upsert({
    where: { id: LOCAL_USER_ID },
    update: data,
    create: { id: LOCAL_USER_ID, ...data },
  });

  return NextResponse.json(pref);
}
