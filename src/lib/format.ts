// ============================================================================
// 표시용 포매팅/라벨 헬퍼 (순수 TS, JSX 없음)
// ----------------------------------------------------------------------------
// 계약값(코드/enum)을 화면 라벨로 바꾸고, 마감일을 "스캔 가능한" 뱃지로 계산한다.
// 오늘 기준은 실제 시스템 시간(new Date()). M1 mock 데이터는 2026-07-01 기준.
// ============================================================================

import {
  DEV_ROLE_OPTIONS,
  EXPERIENCE_OPTIONS,
} from "@/types/contract";
import type { BookmarkStatus, ExperienceLevel } from "@/types/contract";

const MS_PER_DAY = 86_400_000;

/** 직무 코드(value) → 표시 라벨. 알 수 없는 값은 원문 반환. */
export function roleLabel(value: string | null): string | null {
  if (!value) return null;
  return DEV_ROLE_OPTIONS.find((o) => o.value === value)?.label ?? value;
}

/** 경력 enum → 라벨 */
export function experienceLabel(v: ExperienceLevel): string {
  return EXPERIENCE_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

/** 북마크 상태 enum → 라벨 */
export function statusLabel(s: BookmarkStatus): string {
  const map: Record<BookmarkStatus, string> = {
    PLANNED: "지원 예정",
    APPLIED: "지원함",
    CLOSED: "마감",
  };
  return map[s];
}

/** ISO date("2026-07-05") → "2026.07.05" */
export function formatDate(iso: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}.${m}.${day}`;
}

/** 마감일까지 남은 일수(날짜 단위 반올림). 마감일 지났으면 음수. */
export function daysUntilDeadline(deadline: string): number {
  const d = new Date(deadline);
  const target = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
  const now = new Date();
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target - today) / MS_PER_DAY);
}

export type DeadlineTone = "urgent" | "soon" | "normal" | "always" | "closed";

export interface DeadlineInfo {
  label: string;
  tone: DeadlineTone;
  /** 스크린리더/보조 텍스트용 */
  full: string;
}

/**
 * 마감일 → 스캔용 뱃지 정보.
 * - null: 상시채용 → "상시"(정렬 시 항상 맨 뒤)
 * - 지남: "마감"
 * - 오늘: "오늘 마감"(urgent)
 * - D-1~3: urgent / D-4~7: soon / 그 이상: normal
 */
export function deadlineInfo(deadline: string | null): DeadlineInfo {
  if (!deadline) return { label: "상시", tone: "always", full: "상시채용" };
  const days = daysUntilDeadline(deadline);
  if (days < 0) return { label: "마감", tone: "closed", full: "마감된 공고" };
  if (days === 0) return { label: "오늘 마감", tone: "urgent", full: "오늘 마감" };
  const label = `D-${days}`;
  if (days <= 3) return { label, tone: "urgent", full: `마감 ${days}일 전` };
  if (days <= 7) return { label, tone: "soon", full: `마감 ${days}일 전` };
  return { label, tone: "normal", full: `마감 ${days}일 전` };
}
