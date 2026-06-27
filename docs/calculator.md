# Calculator 모듈 사용 문서

이 문서는 계산기 라이브러리 모듈의 사용법·API·설계 제약을 설명한다.
OS.md의 [4단계] 산출물이며, 코드와 어긋나면 본 문서를 현실에 맞게 갱신한다.

> 상태(2026-06-27): 단위/통합 테스트 40/40 통과(green) 기준으로 작성됨.

---

## 1. 개요

- **역할**: 사칙연산 및 거듭제곱·나머지 연산을 제공하는 부수효과 없는 순수 함수형 계산기.
- **패키지**: `ai.genesislab.calculator`
- **위치**: `src/main/java/ai/genesislab/calculator/Calculator.java`
- **특성**: 외부 I/O·가변 상태 없음. 동일 입력에 항상 동일 출력(결정적). CLI/HTTP 없는 순수 라이브러리 레이어.

---

## 2. 공개 API

모든 메서드는 `Calculator`의 **정적 메서드**이며, 파라미터와 반환 타입은 모두 `double`이다.

| 메서드 | 시그니처 | 설명 | 예외 |
|---|---|---|---|
| `add` | `double add(double a, double b)` | `a + b` | 없음 |
| `subtract` | `double subtract(double a, double b)` | `a - b` (a: 피감수, b: 감수) | 없음 |
| `multiply` | `double multiply(double a, double b)` | `a * b` | 없음 |
| `divide` | `double divide(double a, double b)` | `a / b` | `b == 0`이면 `ArithmeticException("Division by zero is not allowed.")` |
| `power` | `double power(double base, double exponent)` | `Math.pow(base, exponent)`. 지수 0 → 1, 음수/실수 지수 지원 | 없음 |
| `modulo` | `double modulo(double a, double b)` | `a % b` | `b == 0`이면 `ArithmeticException("Modulo by zero is not allowed.")` |

### 공개 상수

예외 메시지는 상수로 노출되어 호출 측에서 비교·검증에 재사용할 수 있다.

- `Calculator.DIVIDE_BY_ZERO_MESSAGE` = `"Division by zero is not allowed."`
- `Calculator.MODULO_BY_ZERO_MESSAGE` = `"Modulo by zero is not allowed."`

### 인스턴스화 불가

`Calculator`는 상태가 없는 정적 유틸리티 클래스다. `private` 생성자를 가지며, 리플렉션 등으로 생성을 시도하면 `AssertionError`가 발생한다. `Calculator.add(...)`처럼 클래스 이름으로 직접 호출한다.

---

## 3. 사용 예시 (Java)

### 정상 호출

```java
import ai.genesislab.calculator.Calculator;

double sum        = Calculator.add(2, 3);          // 5.0
double difference = Calculator.subtract(5, 2);     // 3.0
double product    = Calculator.multiply(4, 2.5);   // 10.0
double quotient   = Calculator.divide(10, 4);      // 2.5

double square     = Calculator.power(2, 10);       // 1024.0
double one        = Calculator.power(7, 0);        // 1.0  (지수 0)
double sqrtTwo    = Calculator.power(2, 0.5);      // 1.4142135623730951 (실수 지수)
double reciprocal = Calculator.power(2, -1);       // 0.5  (음수 지수)

double remainder  = Calculator.modulo(10, 3);      // 1.0
```

### 0 나누기 예외 처리

```java
import ai.genesislab.calculator.Calculator;

try {
    double result = Calculator.divide(10, 0);
} catch (ArithmeticException e) {
    // e.getMessage() == "Division by zero is not allowed."
    System.err.println("나눗셈 오류: " + e.getMessage());
}

// modulo도 동일하게 0 제수에서 예외를 던진다.
try {
    double mod = Calculator.modulo(10, 0);
} catch (ArithmeticException e) {
    // e.getMessage() == "Modulo by zero is not allowed."
    System.err.println("나머지 오류: " + e.getMessage());
}
```

---

## 4. 빌드 · 테스트 실행

- 빌드 도구: **Gradle (Kotlin DSL)**, 플러그인 `java-library`.
- 테스트 프레임워크: **JUnit 5 (Jupiter)**.
- 테스트 위치: `src/test/java/ai/genesislab/calculator/`
  - 단위 테스트: `CalculatorTest`
  - 통합 테스트: `CalculatorIntegrationTest`

### 실행

```bash
./gradlew test
```

### JAVA_HOME 주의사항

- 빌드는 Gradle **toolchain으로 Java 21(Corretto 21, LTS)** 을 타깃한다(`build.gradle.kts`).
- 따라서 로컬 `JAVA_HOME`은 **Corretto 21 이상**을 가리키는 것을 권장한다. (Corretto 24도 설치돼 있으면 toolchain 값 변경만으로 동작한다.)
- toolchain이 설정돼 있어 Gradle이 적절한 JDK를 자동 선택하지만, JDK가 전혀 없거나 너무 낮은 버전만 있으면 toolchain 해석에 실패할 수 있다. 이 경우 JDK 21+를 설치하고 `JAVA_HOME`을 맞춘다.
- 참고: `docs/CONVENTIONS.md`는 JDK 23+(corretto-24)를 제안하나, 재현 가능한 LTS 빌드를 위해 실제 빌드는 21을 선택했다.

---

## 5. 주요 설계 결정 · 제약

- **`double` 단일 타입**: 모든 입력·출력을 `double`로 통일해 API를 단순하게 유지한다. 부동소수점 특성상 결과에 반올림 오차가 있을 수 있으므로, 정확한 십진 계산이 필요하면 호출 측에서 `BigDecimal` 등을 사용한다.
- **0 나누기 명시적 예외**: `divide`/`modulo`는 `double`의 기본 동작(`Infinity`/`NaN`)에 의존하지 않고, 제수가 0이면 명시적으로 `ArithmeticException`을 던진다. 메시지는 공개 상수로 제공한다.
- **정적 유틸 클래스**: 상태가 없으므로 인스턴스를 만들지 않는다. `final` 클래스 + `private` 생성자로 강제한다.
- **순수 함수 지향**: 외부 I/O·가변 전역 상태가 없어 동일 입력에 동일 출력을 보장하며, 스레드 안전하다.
- **`power`의 의미**: `Math.pow`에 위임하므로 지수 0 → 1, 음수/실수 지수를 지원하고 그 경계 동작도 `Math.pow`와 동일하다.

---

## 6. 관련 문서

- 저장소 구조·네이밍·테스트·빌드 규칙: [`docs/CONVENTIONS.md`](./CONVENTIONS.md)
- 개발 파이프라인 표준: [`OS.md`](../OS.md)
