# 나만의 클로드 코드 OS 만들기 미션
## 진행 방법
- 나만의 클로드 코드 OS 만들기 주차별 요구사항을 파악한다.
- 요구사항에 대한 구현을 완료한 후 자신의 github 아이디에 해당하는 브랜치에 Pull Request(이하 PR)를 통해 리뷰 요청을 한다.
- 리뷰 피드백에 대한 개선 작업을 하고 다시 PUSH한다.
- 모든 피드백을 완료한 후 merge가 되면 다음 단계를 도전하고 앞의 과정을 반복한다.

## 온라인 코드 리뷰 과정
* [텍스트와 이미지로 살펴보는 온라인 코드 리뷰 과정](https://github.com/next-step/nextstep-docs/tree/master/codereview)

---

# 채용 리서치 웹 서비스 (M1)

취업 준비생용 채용 공고 모아보기 + 회사 리서치 서비스. 전체 청사진은 [`OS.md`](./OS.md) 참조(특히 12장이 개발 계약).

## 기술 스택
Next.js(App Router) + TypeScript + Prisma + SQLite (M1). 상세는 OS.md 12.1.

## 실행 방법
```bash
npm install
npm run db:migrate   # Prisma 마이그레이션 + SQLite(prisma/dev.db) 생성 + seed 자동 실행
npm run db:seed      # (필요 시) mock 데이터 재적재
npm run dev          # http://localhost:3000
npm run collect      # 수집 스텁(현재 MockAdapter 시연, 실 수집은 사람인 API 승인 후)
```

## 공유 타입 (프론트 단일 import 위치)
`src/types/contract.ts` — `JobDTO`, `Job`, `Bookmark`, `BookmarkStatus`, `UserPreference`,
`ExperienceLevel`/`DataQuality`, API 요청/응답 타입, `DEV_ROLE_OPTIONS` 등. 프론트는 `@/types/contract` 만 import.

## Mock API (계약 형태 그대로, 데이터는 mock)
- `GET /api/jobs` (필터/정렬/커서, 빈 결과 `?mock=empty`)
- `GET /api/jobs/:id`
- `GET|PUT /api/me/preferences`

## 사람인 공개 API 유의 (중요)
- 실 공고 수집(SaraminAdapter)에는 **사람인 개발자센터 이용신청 → 승인**이 필요하다. 승인 전까지는 MockAdapter 로 진행.
- 쿼터: **하루 500 콜, 요청당 count ≈ 110 상한**.
- 약관: **재판매·대가 수취 금지**. M1(단일 로컬·비상업 실습)은 무방하나, 공개 서비스화 시 약관/robots 재점검 필요.
