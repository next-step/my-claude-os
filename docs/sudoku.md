# Sudoku 모듈 사용 문서

이 문서는 스도쿠 라이브러리 모듈의 사용법·API·설계 제약을 설명한다.
OS.md의 [4단계] 산출물이며, 코드와 어긋나면 본 문서를 현실에 맞게 갱신한다.

> 상태(2026-06-30): 단위/통합 테스트 132/132 통과(green) 기준으로 작성됨.
>
> **HTTP API 없음(N/A)**: 본 모듈은 순수 라이브러리(JVM 메서드 호출)이므로 엔드포인트가 없다.
> 따라서 `docs/http/` 문서는 작성하지 않는다(crypto 모듈 선례와 동일).

---

## 1. 개요

- **역할**: 9x9 스도쿠 보드의 규칙 검증과 퍼즐 풀이를 제공하는 라이브러리.
  - **검증기(`SudokuValidator`)**: 행/열/3x3 박스에 1~9 중복이 없는지 검사한다. 빈칸(`0`)을 허용해 부분(미완성) 보드도 검증할 수 있고, 완성 보드 여부도 판별한다.
  - **풀이기(`SudokuSolver`)**: 빈칸을 채운 해를 **새 배열로** 반환한다(원본 불변). 백트래킹 + MRV(최소 후보 셀 선택) + 노드 상한 가드. 정통 스도쿠의 **유일해**를 강제한다.
- **패키지**: `ai.genesislab.sudoku`
- **위치**:
  - `src/main/java/ai/genesislab/sudoku/SudokuValidator.java` (검증기)
  - `src/main/java/ai/genesislab/sudoku/SudokuSolver.java` (풀이기)
  - `src/main/java/ai/genesislab/sudoku/SudokuException.java` (모듈 전용 비검사 예외)
- **특성**: CLI/HTTP 없는 순수 라이브러리 레이어. 두 유틸 클래스 모두 상태가 없는 정적 메서드 모음(`final` + private 생성자, 인스턴스화 시 `AssertionError`). 모든 메서드는 **입력 배열을 변형하지 않는다**(부수효과 없음). 외부 의존성 0(순수 JDK).

- **보드 표현**: 보드는 `int[9][9]`이며, 각 칸은 빈칸을 의미하는 `0`(`SudokuValidator.EMPTY`) 또는 `1`~`9`의 숫자다.

---

## 2. 공개 API

### 2-1. `SudokuValidator` — 검증기

모든 메서드는 정적 메서드다.

| 메서드 | 시그니처 | 반환 | 예외 |
|---|---|---|---|
| `isValid` | `static boolean isValid(int[][] board)` | 채워진 칸(1~9)들이 같은 행·열·3x3 박스에서 중복되지 않으면 `true`(빈칸 `0`은 무시 → 부분 보드 검증 가능) | `IllegalArgumentException`(형식 위반) |
| `isComplete` | `static boolean isComplete(int[][] board)` | 빈칸이 하나도 없으면서 `isValid`가 참이면 `true` | `IllegalArgumentException`(형식 위반) |
| `validateFormat` | `static void validateFormat(int[][] board)` | (반환 없음) 형식만 검증. 규칙 위반(중복)은 검사하지 않음 | `IllegalArgumentException`(형식 위반) |

- `validateFormat`은 형식 검증(null·크기·값 범위)을 공통화한 메서드로, `isValid`/`isComplete`/`SudokuSolver.solve`가 공유한다. `isValid`/`isComplete`는 내부에서 먼저 `validateFormat`을 호출한다.

#### 공개 상수

| 상수 | 값 | 의미 |
|---|---|---|
| `SIZE` | `9` | 보드 한 변의 길이 |
| `BOX_SIZE` | `3` | 3x3 박스 한 변의 길이 |
| `EMPTY` | `0` | 빈칸을 나타내는 값 |
| `MIN_VALUE` | `1` | 셀이 가질 수 있는 최소 숫자 |
| `MAX_VALUE` | `9` | 셀이 가질 수 있는 최대 숫자 |
| `NULL_BOARD_MESSAGE` | `"board must not be null."` | 보드가 null일 때 메시지 |
| `NULL_ROW_MESSAGE` | `"board rows must not be null."` | 행이 null일 때 메시지 |
| `INVALID_SIZE_MESSAGE` | `"board must be a 9x9 grid."` | 크기가 9x9가 아닐 때 메시지 |
| `VALUE_OUT_OF_RANGE_MESSAGE` | `"cell values must be between 0 and 9 (0 means empty)."` | 값이 0~9 밖일 때 메시지 |

### 2-2. `SudokuSolver` — 풀이기

모든 메서드는 정적 메서드다.

| 메서드 | 시그니처 | 반환 | 예외 |
|---|---|---|---|
| `solve` | `static int[][] solve(int[][] puzzle)` | 빈칸이 모두 채워진 유일한 해 보드(입력과 독립적인 **새 `int[9][9]`**) | `IllegalArgumentException`(형식 위반·모순 보드), `SudokuException`(해 없음·비유일·탐색 상한 초과) |

- `solve`는 입력을 **복사한 뒤** 백트래킹으로 풀므로 원본 `puzzle`은 변형되지 않는다.
- 검증 순서는 **형식 → 모순 → 풀이**다. 형식 오류와 모순 보드는 `IllegalArgumentException`, 풀이 단계의 도메인 실패(해 없음·비유일·탐색 상한)는 `SudokuException`으로 구분해 던진다.

#### 공개 상수

| 상수 | 값 | 의미 |
|---|---|---|
| `CONTRADICTORY_BOARD_MESSAGE` | `"puzzle is contradictory: it already violates Sudoku rules."` | 입력이 이미 규칙을 위반한 모순 보드 |
| `NO_SOLUTION_MESSAGE` | `"puzzle has no solution."` | 형식·모순은 정상이나 해가 없음 |
| `MULTIPLE_SOLUTIONS_MESSAGE` | `"puzzle does not have a unique solution."` | 해가 둘 이상이라 유일하지 않음 |
| `SEARCH_LIMIT_EXCEEDED_MESSAGE` | `"puzzle search exceeded the node limit; aborting to avoid resource exhaustion."` | 탐색 노드 수가 상한 초과(자원 고갈 방어) |
| `MAX_SEARCH_NODES` | `2_000_000` | 백트래킹이 방문할 수 있는 최대 노드(셀 배치 시도) 수의 방어적 상한 |

> MRV 최적화를 적용한 정상 9x9 유일해 퍼즐은 보통 수천 노드 이내만 방문하므로, 정상 입력은 `MAX_SEARCH_NODES` 가드에 절대 도달하지 않는다.

### 2-3. `SudokuException`

- `class SudokuException extends RuntimeException` — 스도쿠 모듈의 운영/도메인 실패 전용 **비검사 예외**.
- **형식·계약 위반**(null 보드/행, 9x9 아닌 크기, 값 0~9 밖, 이미 규칙을 위반한 모순 보드)은 표준 `IllegalArgumentException`으로 거부한다. 반면 형식은 올바르지만 **풀이 단계에서 발생하는 도메인 실패**(해 없음, 비유일, 탐색 상한 초과)는 `SudokuException`으로 표현한다.
- `CryptoException`과 동일한 구조의 생성자 2종: `SudokuException(String message)`, `SudokuException(String message, Throwable cause)`. JDK 검사 예외를 래핑할 일이 생기면 원인(cause)을 항상 보존한다.

---

## 3. 사용 예시 (Java)

### 검증기 — 부분 보드 검증과 완성 여부 (isValid / isComplete)

```java
import ai.genesislab.sudoku.SudokuValidator;

int[][] partial = /* 일부만 채운 9x9 보드(빈칸은 0) */;

boolean ok = SudokuValidator.isValid(partial);     // 채워진 칸들이 규칙 위반 없으면 true
boolean done = SudokuValidator.isComplete(partial); // 빈칸 없고 규칙도 만족하면 true

// 형식만 검사하고 싶을 때(중복은 보지 않음):
SudokuValidator.validateFormat(partial); // 형식이 잘못되면 IllegalArgumentException
```

### 풀이기 — 퍼즐 풀이 (solve, 원본 불변)

```java
import ai.genesislab.sudoku.SudokuSolver;

int[][] puzzle = /* 단서가 채워진 9x9 퍼즐(빈칸은 0) */;

int[][] solution = SudokuSolver.solve(puzzle); // 빈칸이 모두 채워진 새 int[9][9]
// puzzle 자체는 그대로다(부수효과 없음) — solution은 독립적인 새 배열.
```

### 예외 처리 (형식/모순 vs 도메인 실패 구분)

```java
import ai.genesislab.sudoku.SudokuSolver;
import ai.genesislab.sudoku.SudokuException;

try {
    int[][] solution = SudokuSolver.solve(puzzle);
} catch (IllegalArgumentException e) {
    // 입력 계약 위반: 형식 오류(null·크기·범위) 또는 모순 보드.
    // e.getMessage()를 SudokuValidator.NULL_BOARD_MESSAGE 등 또는
    // SudokuSolver.CONTRADICTORY_BOARD_MESSAGE와 대조해 분기할 수 있다.
} catch (SudokuException e) {
    if (SudokuSolver.NO_SOLUTION_MESSAGE.equals(e.getMessage())) {
        // 해 없음
    } else if (SudokuSolver.MULTIPLE_SOLUTIONS_MESSAGE.equals(e.getMessage())) {
        // 비유일(해 2개 이상, 빈 보드 포함)
    } else if (SudokuSolver.SEARCH_LIMIT_EXCEEDED_MESSAGE.equals(e.getMessage())) {
        // 탐색 노드 상한 초과(자원 고갈 방어) — 정상 퍼즐은 도달 불가
    }
}
```

---

## 4. 에러 계약 (중요)

스도쿠 모듈은 실패를 **입력 계약 위반(`IllegalArgumentException`)** 과 **풀이 도메인 실패(`SudokuException`)** 로 명확히 나눈다. 호출자는 예외 타입과 공개 상수 메시지에 의존해 분기할 수 있다.

### 4-1. 형식 검증 (`SudokuValidator.validateFormat`, `solve` 1단계 공유)

| 입력 상황 | 결과 |
|---|---|
| `board` == null | `IllegalArgumentException` + `NULL_BOARD_MESSAGE` |
| `board.length != 9` 또는 행 길이 `!= 9` | `IllegalArgumentException` + `INVALID_SIZE_MESSAGE` |
| 행(row)이 null | `IllegalArgumentException` + `NULL_ROW_MESSAGE` |
| 셀 값이 0~9 밖(음수, 10 이상) | `IllegalArgumentException` + `VALUE_OUT_OF_RANGE_MESSAGE` |

### 4-2. 풀이 계약 (`SudokuSolver.solve`)

검증 순서가 **형식 → 모순 → 풀이**이므로, 우선순위가 높은 실패가 먼저 던져진다.

| 입력 상황 | 결과 | 예외 타입 |
|---|---|---|
| 형식 위반(4-1 표) | 해당 형식 메시지 | `IllegalArgumentException` |
| 형식은 정상이나 이미 규칙 위반(중복 존재) | `CONTRADICTORY_BOARD_MESSAGE` | `IllegalArgumentException` |
| 형식·모순 정상이나 해가 없음 | `NO_SOLUTION_MESSAGE` | `SudokuException` |
| 해가 둘 이상(비유일, **빈 보드 포함**) | `MULTIPLE_SOLUTIONS_MESSAGE` | `SudokuException` |
| 탐색 노드 수가 `MAX_SEARCH_NODES` 초과 | `SEARCH_LIMIT_EXCEEDED_MESSAGE` | `SudokuException` |

> **왜 모순은 `IllegalArgumentException`이고 해 없음은 `SudokuException`인가**: 모순 보드(이미 중복이 있는 보드)는 풀이를 시도하기도 전에 거부되는 **입력 계약 위반**이다. 반면 "해 없음/비유일"은 형식이 올바른 입력에 대해 풀이 단계에서 도출되는 **도메인 결과**이므로 모듈 전용 예외로 구분한다.
>
> **왜 탐색 상한은 `SudokuException`인가**: 노드 상한 초과는 입력 *형식* 문제가 아니라 풀이 *자원 한계*다. 따라서 `IllegalArgumentException`이 아니라 `SudokuException`으로 던지며, `NO_SOLUTION_MESSAGE`/`MULTIPLE_SOLUTIONS_MESSAGE`와도 메시지로 구분된다. 정상 유일해 퍼즐은 이 가드에 도달하지 않는다.
>
> **빈 보드는 비유일**: 단서가 하나도 없는 빈 보드는 해가 매우 많으므로 `MULTIPLE_SOLUTIONS_MESSAGE`로 거부된다(유일해 강제 정책의 자연스러운 귀결).

---

## 5. 주요 설계 결정 · 제약

- **백트래킹 + MRV 셀 선택**: 다음에 채울 빈칸을 "첫 빈칸"이 아니라 "합법 후보 수가 가장 적은 빈칸"으로 고른다(Minimum Remaining Values). 후보가 적은 칸부터 분기하면 탐색 트리의 분기 폭이 줄어, 단서가 희박한 유효 퍼즐도 트리 폭발 없이 빠르게 푼다. 이 최적화는 **탐색 순서**만 바꿀 뿐 유일해 강제 의미는 그대로다.
- **유일해 강제**: 정통 스도쿠는 유일해를 가진다는 도메인 규칙을 강제한다. 첫 해를 찾은 뒤에도 두 번째 해까지 탐색해, 해가 둘 이상이면 비유일로 보고 `SudokuException`을 던진다(해를 2개까지만 세고 즉시 중단 → 전수 탐색 회피).
- **부수효과 없음(원본 불변)**: `solve`는 입력을 깊은 복사한 뒤 풀고 결과를 새 `int[9][9]`로 반환한다. `isValid`/`isComplete`/`validateFormat`도 입력 배열을 변형하지 않는다.
- **견고성(노드 상한 가드)**: 방문 노드 수를 `MAX_SEARCH_NODES`(=2,000,000)로 제한해, 탐색 트리가 비정상적으로 폭발하는 입력에서 사실상 hang(스레드 자원 고갈)되는 것을 막는다. 자원 한계 방어용이며 정상 퍼즐은 도달 불가.
- **에러 계약의 일관성**: 입력 계약 위반은 `IllegalArgumentException`(공개 상수 메시지), 풀이 도메인 실패는 `SudokuException`으로 통일한다. 모든 예외 메시지는 `public static final` 상수로 노출해 호출 측에서 대조 가능.
- **형식 검증 공통화**: null·크기·값 범위 검증을 `SudokuValidator.validateFormat` 한 곳에 모아 검증기와 풀이기가 재사용한다(중복 로직 제거). `solve`는 `isValid` 호출로 형식 검증과 모순 검사를 한 번에 수행해 형식 패스를 이중으로 돌지 않는다.
- **정적 유틸 클래스**: `SudokuValidator`/`SudokuSolver` 모두 상태가 없으므로 `final` + private 생성자로 인스턴스화를 금지한다(`AssertionError`).
- **의존성 0(순수 JDK)**: 외부 라이브러리 없이 JDK 표준만 사용한다. 빌드 설정 변경 없음.

---

## 6. 빌드 · 의존성 · 테스트 실행

- 빌드 도구: **Gradle (Kotlin DSL)**, 플러그인 `java-library`, **JDK 21 toolchain**.
- 의존성: JDK 표준 라이브러리만 사용 — **외부 의존성 0**.
- 테스트 프레임워크: **JUnit 5 (Jupiter)**.
- 테스트 위치: `src/test/java/ai/genesislab/sudoku/`
  - 단위: `SudokuValidatorTest`, `SudokuSolverTest`
  - 통합: `SudokuIntegrationTest`

### 실행

```bash
./gradlew test
```

### JAVA_HOME 주의사항

- 빌드는 Gradle **toolchain으로 Java 21(Corretto 21, LTS)** 을 타깃한다(`build.gradle.kts`). 로컬 `JAVA_HOME`은 Corretto 21 이상을 권장한다(상세는 `docs/calculator.md` 4절 참고).

---

## 7. 관련 문서

- 저장소 구조·네이밍·테스트·빌드 규칙: [`docs/CONVENTIONS.md`](./CONVENTIONS.md)
- 암호화 모듈 문서(동일 스타일): [`docs/crypto.md`](./crypto.md)
- 계산기 모듈 문서(동일 스타일): [`docs/calculator.md`](./calculator.md)
- 개발 파이프라인 표준: [`OS.md`](../OS.md)
