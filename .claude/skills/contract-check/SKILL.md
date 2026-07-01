---
name: contract-check
description: 백엔드가 정의·export한 공유 타입(Job/JobDTO/Bookmark/UserPreference 등 계약)과 프론트엔드가 실제로 사용하는 형태 사이의 어긋남(drift)을 점검한다. 백엔드·프론트가 병렬로 일한 뒤 "계약이 맞는지 확인해줘", "타입 드리프트 점검", "contract-check", API 응답과 화면이 안 맞을 때 사용.
---

# Contract Check — 백엔드↔프론트 계약 드리프트 점검

두 개발 에이전트는 **OS.md 12장의 계약**(타입·API 스펙)을 공유 진실로 삼아 병렬로 일한다.
계약의 단일 출처는 **backend가 정의해 export하는 TypeScript 타입**이고, frontend는 이를 import해 쓴다.
이 스킬은 그 계약이 세 군데(① OS.md 12장 ② backend 실제 타입 ③ frontend 사용처)에서 **서로 어긋나지 않는지** 점검한다.

## 언제 쓰나
- backend가 타입/엔드포인트를 바꾼 뒤, frontend가 그걸 따라갔는지 확인할 때.
- frontend가 화면에서 필요한 필드가 계약에 있는지 확인할 때.
- 통합(Mock→실 API 교체) 직전, 양쪽이 같은 모양을 가정하는지 마지막 점검할 때.

## 점검 대상 (현재 계약의 핵심 객체)
OS.md 12.3~12.5 기준. 바뀌면 OS.md를 먼저 본다.
- `Job` / `JobDTO`(= Job + `sources[]` + `duplicateCount` + `bookmark`)
- `Bookmark`(status: PLANNED|APPLIED|CLOSED, memo, createdAt)
- `UserPreference`(roles[], locations[], experience, keywords[])
- API 엔드포인트 8종(GET /api/jobs … DELETE /api/bookmarks/:id)과 요청/응답 형태.

## 절차

1. **세 출처를 모은다.**
   - OS.md 12장(계약 명세).
   - backend의 타입 정의 파일(예: `types`·`schema`·Prisma 스키마에서 생성된 타입). 위치를 모르면 Glob/Grep으로 `Job`, `JobDTO`, `Bookmark`, `UserPreference` 선언을 찾는다.
   - frontend에서 그 타입을 import하거나 응답을 구조분해(`res.items`, `job.deadline` 등)하는 지점.

2. **필드 단위로 대조한다.** 각 객체에 대해:
   - **이름·유무**: 한쪽엔 있는데 다른 쪽엔 없는 필드(예: backend가 `dataQuality`를 추가했는데 frontend가 모름).
   - **타입·nullability**: `deadline: string | null`을 frontend가 non-null로 가정하는가? `description`이 null일 때 폴백 처리가 있는가?
   - **enum 값**: `experienceLevel`/`status`의 리터럴 집합이 양쪽 동일한가(오타·누락 값).
   - **API 요청/응답**: frontend가 보내는 query/body가 endpoint 스펙과 일치하는가, 응답 구조(`{items,nextCursor,totalCount,partialHiddenCount}`)를 그대로 받는가.

3. **드리프트를 분류해 보고한다.**
   - 🔴 **불일치(깨짐)**: 런타임/컴파일 에러나 빈 화면을 유발. 예) frontend가 쓰는 필드가 응답에 없음, null 미처리.
   - 🟡 **표류(위험)**: 지금은 동작하나 한쪽만 바뀌어 곧 깨질 것. 예) OS.md엔 추가됐는데 코드 미반영.
   - 🟢 **일치**: 문제없음.

4. **고치는 방향을 제시한다(코드를 함부로 바꾸지 않는다).**
   - 원인이 어느 쪽인지(계약/백/프론트) 짚고, **계약 자체를 바꿔야 하면 OS.md 12장 갱신이 선행**임을 명시한다(그건 기획자 권한 — 필요 시 기획자에게 되돌릴 사안).
   - 단순히 한쪽이 계약을 안 따라간 것이면 그쪽을 맞추도록 구체적 위치와 수정안을 제시한다.

## 원칙
- **단일 출처 우선**: 셋이 어긋나면 기준은 "OS.md 12장 = 합의된 계약". 코드가 계약을 벗어났으면 코드를 맞춘다. 계약 자체가 틀렸으면 기획자에게 올려 OS.md를 먼저 고친다.
- **타입스크립트의 이점 활용**: 가능하면 `tsc --noEmit`(타입체크)나 `npx tsc -p .`로 컴파일 단계 드리프트를 먼저 잡고, 그 출력으로 점검을 보강한다.
- 이 스킬은 **점검·보고**가 기본이다. 코드 수정이 필요하면 무엇을 왜 고칠지 먼저 제시하고 진행한다.

## 출력 형태
객체별 표(필드 / 계약 / backend / frontend / 판정 🔴🟡🟢) → 🔴·🟡 항목의 원인과 수정 방향 → 계약 변경이 필요한 항목은 "기획자에게 OS.md 12장 갱신 요청" 표시.
