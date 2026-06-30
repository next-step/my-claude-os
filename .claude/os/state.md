# OS 진행 상태 (state)

> 오케스트레이터(`/os`)가 단계 전이마다 갱신한다. 이 파일이 있으면 진행 중인 작업이다.

stage: done         # 1 | 2 | 3 | 4 | done
started_at: 2026-06-29
updated_at: 2026-06-30

## 요구사항 (확정된 계약서 — 각 에이전트에 전달)
- 한 줄 요약: 스도쿠 9x9 검증기(Validator) + 풀이기(Solver) Java 라이브러리 모듈. 순수 로직, 외부 의존성 0.
- 입력/출력:
  - 검증기: `isValid(int[][] board) -> boolean` (행/열/3x3 박스 중복 없으면 true, 빈칸 0은 허용=부분 보드 검사), `isComplete(int[][] board) -> boolean` (빈칸 없고 + isValid).
  - 풀이기: `solve(int[][] puzzle) -> int[][]` (빈칸 0을 채운 해 보드 반환, 백트래킹).
- 경계 조건: 빈 보드(전부 0), 거의 다 찬 보드, 단 한 칸만 빈 보드, 대각선/박스 경계 중복.
- 예외 케이스: null 보드/행, 9x9 아닌 크기, 값 범위(0~9) 밖, 이미 모순된 입력 보드, 해가 없는 퍼즐, 해가 여러 개인 퍼즐.

## 확정된 설계 결정 (결정 게이트 ① 산출 — 사람 승인됨)
> 회색지대 결정을 추천 기본값과 함께 사람이 confirm/override한 결과. 각 에이전트 위임 시 함께 전달한다.
- 공개 API 계약(시그니처·반환·에러 처리): 검증기=isValid/isComplete(boolean 반환). 풀이기=solve(int[][])->int[][]. **에러는 예외 throw**: 잘못된 형식(null/9x9 아님/값 0~9 밖)·모순 입력(규칙 위반 중복) → IllegalArgumentException; **해 없음 → 전용 SudokuException**. 반환 보드는 새 배열(원본 불변).
- 도메인 정확성(스도쿠 규칙·해 정책): **유일해 강제** — solve는 해를 2개까지 탐색해 유일성을 검증한다. 해가 2개 이상이면 SudokuException(비유일). 표준 9x9, 3x3 박스 9개, 1~9 값, 0=빈칸.
- 환경/빌드(빌드도구·런타임/toolchain 버전): 기존 Gradle(Kotlin DSL) + Java 21. 순수 로직이라 외부 의존성 0(JDK 표준 라이브러리만). [AI 결정·로그]
- 비기능 요건(스레드 안전성·성능·의존성): 정적 메서드 모음, 부수효과 없는 순수 함수 지향. 의존성 추가 없음. [AI 결정·로그]
- AI 자체 결정(묻지 않고 정한 항목 로그): 패키지명(ai.genesislab.sudoku 권장)/클래스명/테스트 파일 명명/내부 백트래킹 구현 세부는 1단계 컨벤션 따라 os-developer가 결정.

## 게이트 통과 기록
- [x] 결정 게이트 ① — 사람 (2단계 위임 전, 설계 결정 승인) — AskUserQuestion 3건: 예외 throw / 유일해 강제(override) / 새 배열 반환
- [x] 리뷰 게이트 — AI (1차 high 워크플로우: 블로킹 #1+갭/정리 → "핵심만 수정"(#1·#6·#8·#2) → 재검증 132 green. 2차 워크플로우는 인프라 실패 → 단일 집중 리뷰로 대체: 블로킹 4건 전부 해소·MRV 정확성 확인·새 블로킹 0 → 통과)
- [x] 수용 게이트 ② — 사람 (done 전, 의도 일치·누락 없음 확인) — 사용자 "수용 — done 처리"

## DoD 체크리스트 (OS.md 기준)
### 1단계 — 코드베이스·컨벤션 파악
- [x] CONVENTIONS 문서가 현재 코드와 일치 (drift 3건만 갱신)
- [x] 재사용 가능한 기존 자산 식별됨
### 2단계 — 분석·개발·테스트 작성
- [x] 모든 요구사항이 코드로 구현됨
- [x] 각 요구사항에 대응하는 단위·통합 테스트 존재 (단위33+통합5=38)
- [x] 기존 자산 재사용(또는 불가 사유 명확)
### 3단계 — 검증 루프
- [x] 모든 단위·통합 테스트 green (clean test 129 passed: calc40+crypto51+sudoku38)
- [x] skip/가짜 통과 테스트 없음 (grep 0건)
### 4단계 — 문서화
- [x] 새 기능/변경점이 docs에 반영 (docs/sudoku.md)
- [x] API 변경이 HTTP 문서에 반영 (라이브러리 → HTTP 없음 N/A)

## 산출물 경로
- CONVENTIONS: docs/CONVENTIONS.md (1단계 drift 갱신)
- 코드: src/main/java/ai/genesislab/sudoku/{SudokuValidator,SudokuSolver,SudokuException}.java
- 테스트: src/test/java/ai/genesislab/sudoku/{SudokuValidatorTest,SudokuSolverTest,SudokuIntegrationTest}.java
- 문서: docs/sudoku.md (신규), docs/CONVENTIONS.md (도메인 목록 갱신). HTTP는 API 부재로 N/A

## 단계별 로그
- [1단계] 완료. os-mapper가 코드 직접 대조 후 docs/CONVENTIONS.md drift 3건만 갱신(crypto 파일/테스트/testutil 누락, bcrypt 의존성 블록, CryptoException 예외 패턴·assertNotInstantiable 헬퍼). 재사용 자산: AesGcmCipher 정적 유틸 골격(final+private 생성자 AssertionError), public static final 메시지 상수 패턴, CryptoException→SudokuException 복제, testutil/UtilityClasses.assertNotInstantiable, 단위/통합 테스트 템플릿(@Nested+한글 @DisplayName+assertThrows). 권장 배치: ai.genesislab.sudoku 패키지(SudokuValidator/SudokuSolver/SudokuException + 테스트 3종). 빌드 변경 불필요(의존성 0). toolchain 경고: IDE 24 vs 빌드 21 불일치하나 계약(21) 기준 빌드 정상.
- [2단계] 완료. ai.genesislab.sudoku 패키지에 SudokuValidator(isValid/isComplete, validateFormat 공통화), SudokuSolver(백트래킹+해 2개까지 탐색해 유일해 강제, 새 배열 반환), SudokuException(CryptoException 패턴 복제) 구현. 에러 계약: 형식/모순→IllegalArgumentException(CONTRADICTORY_BOARD_MESSAGE), 해없음→SudokuException(NO_SOLUTION), 비유일→SudokuException(MULTIPLE_SOLUTIONS, 빈 보드 포함). 단위33(Validator17+Solver16)+통합5=38 테스트. 재사용: 정적 골격/상수 패턴/SudokuException/assertNotInstantiable/테스트 템플릿. build.gradle.kts 변경 0. developer 자체 실행 38 PASSED.
- [3단계] 1차 완료. os-verifier가 JAVA_HOME=corretto-21로 ./gradlew clean test → BUILD SUCCESSFUL, 129 passed/0 failed/0 skipped. skip·가짜통과 grep 0건, build.gradle.kts 변경 0.
- [3단계 재검증] 완료. os-verifier 독립 재확인: JAVA_HOME=corretto-21 clean test → 132 passed(calc40+crypto51+sudoku41)/0 failed/0 skipped. 수정 없음(첫 실행 green). #1 가드(MAX_SEARCH_NODES/SEARCH_LIMIT_EXCEEDED_MESSAGE)·#2 행인스턴스 단언·#6 getMessage 상수대조 모두 진짜 단언 눈검증. SearchRobustness 0.028s(2초 상한, ~70배 여유) flaky 아님. build.gradle.kts 변경 0.
- [리뷰 게이트] 1차: 워크플로우 /code-review high (finder 8앵글/후보32/검증→16기각·10보고). 하드 버그 0건. 🟠견고성1: #1 countSolutions가 MRV·반복가드 없이 첫 빈칸 탐색→희박/어려운 퍼즐서 탐색폭발=사실상 hang(DoS). 🟡테스트갭5: #2 불변테스트 최상위참조만(행배열 미검증), #6 통합테스트 assertThrows 타입만(getMessage 상수대조 누락=CONVENTIONS§5 위반), #3 해 정확히2개 경계 미검증, #4 모순테스트 행중복만(열·박스 누락), #5 깊은 해없음 경로 미검증, #7 형식∧모순 우선순위 미고정. 🟢정리3: #8 형식검증 2회중복, #9 제약로직 Validator/Solver 이중구현, #10 deep-copy 3곳중복. 사용자 결정: "핵심만 수정"(#1·#6·#8·#2) → 2단계 재위임. 나머지(#3,4,5,7,9,10)는 기록만(수용).
- [리뷰 게이트 2차] 워크플로우 /code-review high 재실행이 인프라 오류로 실패(StructuredOutput retry cap exceeded, 판정 아님). 비례 판단: 3파일 타깃 수정 + os-verifier 눈검증 완료라 무거운 워크플로우 재시도 대신 단일 집중 리뷰 에이전트로 대체. 결과: 게이트 통과 가능. #1(MRV+가드: 카운팅 불변·off-by-one 없음·false positive 불가)·#8(우선순위 보존)·#6(상수 대조)·#2(행 인스턴스 비교) 4건 모두 해소. MRV 재작성 카운팅·계약·가지치기·가드 정확, 새 블로킹 0. 경미(범위밖): 가드 발동경로 자체 미커버(테스트 갭 동류).
- [4단계] 완료. os-documenter가 docs/sudoku.md 신규 작성(개요·공개API표·사용예시·에러계약표·설계결정[백트래킹+MRV·유일해강제·원본불변·노드가드·의존성0]·빌드). crypto/calculator 문서 구조 준수, 세 소스 직접 대조로 상수·시그니처 일치 확인. docs/CONVENTIONS.md 도메인 목록 calculator/crypto/sudoku로 갱신. HTTP는 라이브러리라 N/A. 코드/테스트 미변경.
