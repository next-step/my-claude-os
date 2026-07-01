// ============================================================================
// 데이터 접근 계층 (SERVER READS) — 단일 진입점
// ----------------------------------------------------------------------------
// 화면은 이 파일의 함수만 호출한다. Mock API → 실 API 교체 시 이 파일만 바뀌고
// 화면 코드는 그대로다(응답 계약 JobDTO/JobsListResponse 동일).
//
// [범위] 서버가 이미 구현한 읽기 API만 여기 둔다:
//   GET /api/jobs, GET /api/jobs/:id, GET|PUT /api/me/preferences
// 북마크 쓰기 API(POST/PATCH/DELETE)는 아직 미구현 → src/lib/bookmarks.tsx 의
//   클라이언트 스토어가 담당(낙관적 업데이트). 실 라우트가 나오면 그 파일에서
//   아래 fetch 로 교체하면 된다.
// ============================================================================

import type {
  JobDTO,
  JobsListResponse,
  UserPreference,
  UpdatePreferenceBody,
  JobSort,
  ApiError,
} from "@/types/contract";

/** 피드 필터 상태(화면 ↔ 쿼리 직렬화 사이의 단일 형태) */
export interface FeedFilters {
  /** API 는 role 단일값만 지원(12.5). null = 전체 */
  role: string | null;
  locations: string[];
  experiences: string[];
  keyword: string;
  sort: JobSort;
  deadlineWithin: number | null;
  includeExpired: boolean;
  cursor?: string | null;
}

export const DEFAULT_FILTERS: FeedFilters = {
  role: null,
  locations: [],
  experiences: [],
  keyword: "",
  sort: "deadline",
  deadlineWithin: null,
  includeExpired: false,
  cursor: null,
};

/** 실패 응답을 계약 에러 형태(ApiError)로 파싱해 던진다. */
export class ApiRequestError extends Error {
  status: number;
  code: string;
  constructor(message: string, status: number, code = "UNKNOWN") {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.code = code;
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `요청에 실패했어요 (${res.status})`;
    let code = "HTTP_" + res.status;
    try {
      const body = (await res.json()) as ApiError;
      if (body?.error?.message) message = body.error.message;
      if (body?.error?.code) code = body.error.code;
    } catch {
      /* 본문 없음 */
    }
    throw new ApiRequestError(message, res.status, code);
  }
  return (await res.json()) as T;
}

/** FeedFilters → GET /api/jobs 쿼리스트링(12.5/12.6 규약대로 직렬화) */
export function buildJobsQuery(f: FeedFilters): string {
  const p = new URLSearchParams();
  if (f.role) p.set("role", f.role);
  if (f.locations.length) p.set("location", f.locations.join(","));
  if (f.experiences.length) p.set("experience", f.experiences.join(","));
  if (f.keyword.trim()) p.set("keyword", f.keyword.trim());
  p.set("sort", f.sort);
  if (f.deadlineWithin != null) p.set("deadlineWithin", String(f.deadlineWithin));
  if (f.includeExpired) p.set("includeExpired", "true");
  if (f.cursor) p.set("cursor", f.cursor);
  return p.toString();
}

export async function fetchJobs(
  f: FeedFilters,
  signal?: AbortSignal
): Promise<JobsListResponse> {
  const res = await fetch(`/api/jobs?${buildJobsQuery(f)}`, {
    cache: "no-store",
    signal,
  });
  return parse<JobsListResponse>(res);
}

export async function fetchJob(id: string, signal?: AbortSignal): Promise<JobDTO> {
  const res = await fetch(`/api/jobs/${encodeURIComponent(id)}`, {
    cache: "no-store",
    signal,
  });
  return parse<JobDTO>(res);
}

export async function fetchPreferences(
  signal?: AbortSignal
): Promise<UserPreference> {
  const res = await fetch(`/api/me/preferences`, { cache: "no-store", signal });
  return parse<UserPreference>(res);
}

export async function savePreferences(
  body: UpdatePreferenceBody
): Promise<UserPreference> {
  const res = await fetch(`/api/me/preferences`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return parse<UserPreference>(res);
}

/**
 * 저장(Saved) 화면용: 마감 포함 전체 공고를 커서로 끝까지 모아온다.
 * GET /api/bookmarks 가 아직 미구현이라, 전체 공고를 가져와 클라이언트 북마크
 * 스토어와 교집합을 취하는 방식(계약대로 마감 공고도 포함 표시).
 * 실 GET /api/bookmarks 가 나오면 이 함수 대신 그 엔드포인트로 교체한다.
 */
export async function fetchAllJobsIncludingExpired(
  signal?: AbortSignal
): Promise<JobDTO[]> {
  const all: JobDTO[] = [];
  let cursor: string | null = null;
  // 안전장치: 커서가 잘못 돌아 무한루프가 되지 않도록 상한.
  for (let guard = 0; guard < 50; guard++) {
    const page: JobsListResponse = await fetchJobs(
      { ...DEFAULT_FILTERS, includeExpired: true, cursor },
      signal
    );
    all.push(...page.items);
    if (!page.nextCursor) break;
    cursor = page.nextCursor;
  }
  return all;
}
