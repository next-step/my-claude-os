# Claude OS 동작 테스트

이 OS는 **결정적 부품**(훅 JS·셸 스크립트·스킬 구조)과 **LLM 의존 부품**(스킬 오케스트레이션)이
섞여 있다. 자동화 난이도가 완전히 다르므로 테스트를 3계층으로 나눈다.

| 계층 | 파일 | 검사 대상 | 성격 | 비용 |
|------|------|-----------|------|------|
| **L1** | `tests/lint.sh` | frontmatter·참조 링크 무결성·문법·JSON 스키마 | 결정적·정적 | 즉시 |
| **L2** | `tests/unit.test.js` | `detect-todo.js` 훅의 stdin→stdout 계약 | 결정적·단위 | 즉시 |
| **L3** | `tests/smoke.sh` | `claude -p "/capture"` → Notion 저장 end-to-end | 비결정적·통합 | 수십 초·자격증명 필요 |

> **왜 이렇게 나눴나:** LLM 흐름까지 매번 돌리면 느리고 비결정적이라 CI 가 불안정해진다.
> 가장 자주 깨지는 건 사실 "파일명 바꿨더니 참조가 끊긴" 같은 구조 문제(L1)다.
> 그래서 **결정적 L1·L2 를 게이트로** 두고, 진짜 동작 확인(L3)은 필요할 때만 돌린다.

## 실행 방법

```bash
./run-tests.sh          # 전체 (L1 → L2 → L3)
./run-tests.sh l1 l2    # 결정적 계층만 (CI 가 쓰는 조합, 빠름)
./run-tests.sh l3       # 통합 스모크만
```

## 각 계층이 잡는 것

### L1 — 정적 검증 (`lint.sh`)
- 모든 `SKILL.md` 에 `name`/`description` frontmatter 가 있는가
- 스킬·에이전트 md 가 참조하는 `.claude/...(md|sh|js)` 경로가 **실재**하는가
  → 파일명을 바꾸면 런타임에야 깨지는 끊어진 링크를 커밋 전에 잡는다
- 훅 JS 문법(`node --check`), 셸 문법(`bash -n`)
- `data/*.json` 이 valid JSON 이고 필수 키를 갖는가 (비밀값이라 없으면 SKIP)

### L2 — 훅 단위 테스트 (`unit.test.js`)
`detect-todo.js` 는 stdin(JSON)→stdout(JSON|빈출력)인 순수 함수에 가깝다.
의존성 없이 실제 훅을 실행해 두 계약을 검증한다:
- 할일 뉘앙스 + 명령/질문/슬래시 아님 → **capture 힌트 출력**
- 그 외(명령·질문·슬래시·무관·빈입력) → **침묵**

### L3 — headless 통합 스모크 (`smoke.sh`)
1. 고유 마커 제목으로 `claude -p "/capture <marker>"` 를 실제 실행
2. `notion.sh read draft` 로 그 항목이 생성·분류됐는지 확인
3. 만든 테스트 항목을 **아카이브**해서 DB 오염 방지

`claude` CLI·`notion.json`·`jq` 중 하나라도 없으면 **SKIP**(실패 아님) → CI 에서 안전.

## CI

`.github/workflows/ci.yml` 가 push·PR 마다 **L1+L2** 를 돌린다.
L3 는 자격증명과 `claude` CLI 가 필요해 CI 에선 생략하고, 로컬에서 수동 실행한다.

## 테스트 추가하기

- **새 훅**을 만들면 → `unit.test.js` 에 케이스 테이블 한 줄 추가
- **새 스킬**을 만들면 → L1 이 자동으로 frontmatter·링크를 검사 (별도 작업 불필요)
- **새 데이터 파일**을 쓰면 → `lint.sh` 의 `check_json` 한 줄 추가
