// GET/PUT /api/me/preferences — [MOCK-backed] 사용자 조건. 계약: UserPreference (OS.md 12.5).
// M1 단일 로컬 사용자. 실 DB(UserPreference id="local") 연동은 다음 M1 작업에서 교체.
import { NextRequest, NextResponse } from "next/server";
import { MOCK_PREFERENCE } from "@/lib/mock/preferences";
import type { UserPreference } from "@/types/contract";

// mock 이므로 프로세스 메모리에만 반영(재시작 시 초기화). 계약 확인용.
let current: UserPreference = { ...MOCK_PREFERENCE };

export async function GET() {
  return NextResponse.json(current);
}

export async function PUT(req: NextRequest) {
  const body = (await req.json()) as Partial<UserPreference>;
  current = {
    roles: body.roles ?? current.roles,
    locations: body.locations ?? current.locations,
    experience: body.experience ?? current.experience,
    keywords: body.keywords ?? current.keywords,
  };
  return NextResponse.json(current);
}
