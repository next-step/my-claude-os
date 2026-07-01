// ============================================================================
// Mock seed 데이터 (JobDTO[]) — 프론트 unblock 용
// ----------------------------------------------------------------------------
// 실 SaraminAdapter/Normalizer 없이도 프론트가 4화면(온보딩/피드/상세/저장)을
// 만들 수 있도록 개발직군 가짜 공고를 제공한다. 실 수집기 완성 시 이 모듈은
// DB 데이터로 교체된다(계약 JobDTO 는 동일 → 프론트 수정 불필요).
//
// 포함 케이스 (요구사항):
//  - FULL 공고 여러 개
//  - PARTIAL 폴백 공고 최소 1개 (핵심 필드 null → 원문 직접 확인 CTA)
//  - deadline=null 상시채용 1개 (정렬 시 맨 뒤, "상시" 뱃지)
//  - 마감 지난 공고 1개 (기본 피드에서 제외, includeExpired=true 시 노출)
//  - 북마크 있는/없는 케이스 혼합 (PLANNED / APPLIED / null)
//
// 기준 날짜: 2026-07-01 (오늘). 이 이전 deadline = 마감(expired).
// ============================================================================

import type { JobDTO } from "@/types/contract";

/** 회사+직무+지역 → dedupKey (12.3 규약, 마감일 제외). 실 Normalizer 도 동일 규칙 사용 예정. */
function dedupKey(company: string, role: string | null, location: string | null): string {
  return [company, role ?? "", location ?? ""].join("|").toLowerCase();
}

export const MOCK_JOBS: JobDTO[] = [
  // 1) FULL · 마감임박 · 북마크(PLANNED)
  {
    id: "job_dev_001",
    source: "saramin",
    sourceJobId: "SR-1001",
    url: "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1001",
    title: "백엔드 개발자 (Node.js/TypeScript) 신입",
    companyName: "토스뱅크",
    companyId: null,
    jobRole: "backend",
    location: "서울",
    experienceLevel: "NEW",
    employmentType: "정규직",
    deadline: "2026-07-05",
    postedAt: "2026-06-20",
    description:
      "Node.js/TypeScript 기반 결제 백엔드 API 개발. REST/gRPC, PostgreSQL, 대용량 트래픽 경험 우대.",
    dataQuality: "FULL",
    dedupKey: dedupKey("토스뱅크", "backend", "서울"),
    collectedAt: "2026-06-30T09:00:00.000Z",
    sources: ["saramin"],
    duplicateCount: 1,
    bookmark: { bookmarkId: "bm_001", status: "PLANNED" },
  },

  // 2) FULL · 경력 · 북마크 없음
  {
    id: "job_dev_002",
    source: "saramin",
    sourceJobId: "SR-1002",
    url: "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1002",
    title: "프론트엔드 개발자 (React) 경력",
    companyName: "당근마켓",
    companyId: null,
    jobRole: "frontend",
    location: "경기",
    experienceLevel: "EXPERIENCED",
    employmentType: "정규직",
    deadline: "2026-07-15",
    postedAt: "2026-06-22",
    description:
      "React/Next.js 기반 웹 서비스 프론트엔드 개발. 대규모 트래픽 SPA 경험 3년 이상.",
    dataQuality: "FULL",
    dedupKey: dedupKey("당근마켓", "frontend", "경기"),
    collectedAt: "2026-06-30T09:00:00.000Z",
    sources: ["saramin"],
    duplicateCount: 1,
    bookmark: null,
  },

  // 3) FULL · 마감 매우 임박 · 경력무관
  {
    id: "job_dev_003",
    source: "saramin",
    sourceJobId: "SR-1003",
    url: "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1003",
    title: "데이터 엔지니어 (Spark/Airflow)",
    companyName: "쿠팡",
    companyId: null,
    jobRole: "data",
    location: "서울",
    experienceLevel: "ANY",
    employmentType: "정규직",
    deadline: "2026-07-03",
    postedAt: "2026-06-18",
    description: "대규모 데이터 파이프라인 설계/운영. Spark, Airflow, AWS 경험.",
    dataQuality: "FULL",
    dedupKey: dedupKey("쿠팡", "data", "서울"),
    collectedAt: "2026-06-30T09:00:00.000Z",
    sources: ["saramin"],
    duplicateCount: 1,
    bookmark: null,
  },

  // 4) PARTIAL 폴백 공고 — 핵심 필드 null (jobRole/location/description null), deadline null
  //    프론트는 전용 카드 + "원문에서 직접 확인" CTA(url) 로 처리.
  {
    id: "job_dev_004",
    source: "saramin",
    sourceJobId: "SR-1004",
    url: "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1004",
    title: "[채용] 서버 개발자 모집",
    companyName: "스타트업A",
    companyId: null,
    jobRole: null,
    location: null,
    experienceLevel: "ANY",
    employmentType: null,
    deadline: null,
    postedAt: "2026-06-25",
    description: null,
    dataQuality: "PARTIAL",
    dedupKey: dedupKey("스타트업A", null, null),
    collectedAt: "2026-06-30T09:00:00.000Z",
    sources: ["saramin"],
    duplicateCount: 1,
    bookmark: null,
  },

  // 5) FULL · 상시채용(deadline=null) · 원격
  {
    id: "job_dev_005",
    source: "saramin",
    sourceJobId: "SR-1005",
    url: "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1005",
    title: "DevOps 엔지니어 (상시채용)",
    companyName: "네이버클라우드",
    companyId: null,
    jobRole: "devops",
    location: "원격",
    experienceLevel: "EXPERIENCED",
    employmentType: "정규직",
    deadline: null,
    postedAt: "2026-06-10",
    description: "Kubernetes/Terraform 기반 인프라 자동화. 상시 채용.",
    dataQuality: "FULL",
    dedupKey: dedupKey("네이버클라우드", "devops", "원격"),
    collectedAt: "2026-06-30T09:00:00.000Z",
    sources: ["saramin"],
    duplicateCount: 1,
    bookmark: null,
  },

  // 6) FULL · 마감 지남(expired, 2026-06-20 < 오늘) → 기본 피드 제외
  {
    id: "job_dev_006",
    source: "saramin",
    sourceJobId: "SR-1006",
    url: "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1006",
    title: "안드로이드 개발자 (Kotlin) 신입",
    companyName: "카카오모빌리티",
    companyId: null,
    jobRole: "android",
    location: "부산",
    experienceLevel: "NEW",
    employmentType: "정규직",
    deadline: "2026-06-20",
    postedAt: "2026-06-01",
    description: "Kotlin 기반 안드로이드 앱 개발. Jetpack Compose 경험 우대.",
    dataQuality: "FULL",
    dedupKey: dedupKey("카카오모빌리티", "android", "부산"),
    collectedAt: "2026-06-30T09:00:00.000Z",
    sources: ["saramin"],
    duplicateCount: 1,
    bookmark: null,
  },

  // 7) FULL · 북마크(APPLIED) — 저장 화면 상태관리 테스트용
  {
    id: "job_dev_007",
    source: "saramin",
    sourceJobId: "SR-1007",
    url: "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1007",
    title: "풀스택 개발자 (TypeScript)",
    companyName: "무신사",
    companyId: null,
    jobRole: "fullstack",
    location: "서울",
    experienceLevel: "ANY",
    employmentType: "정규직",
    deadline: "2026-07-25",
    postedAt: "2026-06-24",
    description: "Next.js + NestJS 풀스택 개발. 커머스 도메인 경험 우대.",
    dataQuality: "FULL",
    dedupKey: dedupKey("무신사", "fullstack", "서울"),
    collectedAt: "2026-06-30T09:00:00.000Z",
    sources: ["saramin"],
    duplicateCount: 1,
    bookmark: { bookmarkId: "bm_002", status: "APPLIED" },
  },

  // 8) FULL · iOS · 인천 · 경력 (description 있음, deadline=null 아님)
  {
    id: "job_dev_008",
    source: "saramin",
    sourceJobId: "SR-1008",
    url: "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1008",
    title: "iOS 개발자 (Swift) 경력",
    companyName: "라인",
    companyId: null,
    jobRole: "ios",
    location: "인천",
    experienceLevel: "EXPERIENCED",
    employmentType: "정규직",
    deadline: "2026-07-12",
    postedAt: "2026-06-19",
    description: "Swift/SwiftUI 기반 iOS 앱 개발. 5년 이상.",
    dataQuality: "FULL",
    dedupKey: dedupKey("라인", "ios", "인천"),
    collectedAt: "2026-06-30T09:00:00.000Z",
    sources: ["saramin"],
    duplicateCount: 1,
    bookmark: null,
  },
];

/** 빈/에러 형태 표현용 — GET /api/jobs?mock=empty 등에서 사용 가능 */
export const MOCK_JOBS_EMPTY: JobDTO[] = [];

/** id 로 단건 조회 (GET /api/jobs/:id mock 용) */
export function findMockJob(id: string): JobDTO | undefined {
  return MOCK_JOBS.find((j) => j.id === id);
}
