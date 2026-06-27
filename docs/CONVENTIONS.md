# CONVENTIONS

이 문서는 저장소의 구조·네이밍·테스트·빌드·코드 스타일 규칙을 고정한다.
OS.md의 [1단계] 산출물이며, 코드와 어긋나면 이 문서를 현실에 맞게 갱신한다.

> 상태(2026-06-27 갱신): 계산기 모듈(`ai.genesislab.calculator`)이 실제로 구현되어
> Gradle 빌드·JUnit 5 테스트가 동작한다(`./gradlew test` BUILD SUCCESSFUL 확인).
> 따라서 본 문서는 더 이상 "제안"이 아니라 **현재 코드에서 관측된 사실**을 기준으로 한다.
> 이후 모듈(예: 암호화 모듈)은 아래 컨벤션을 그대로 따른다.

---

## 1. 현재 저장소 구조 (사실)

```
my-claude-code-os/
├── .claude/                 # Claude OS 런타임 (에이전트·스킬·훅·상태)
│   ├── agents/              # os-mapper / os-developer / os-verifier / os-documenter
│   ├── hooks/               # count-requests.sh
│   ├── os/                  # state.md, state.template.md
│   └── skills/              # commit, os, skill-stat
├── .idea/                   # IntelliJ 설정 (languageLevel JDK_23, project SDK corretto-24)
├── CLAUDE.md                # 프로젝트 지침
├── OS.md                    # 개발 파이프라인(1~4단계) 표준 문서
├── README.md                # 미션 안내
├── settings.gradle.kts      # rootProject.name = "calculator"
├── build.gradle.kts         # java-library 플러그인, JUnit 5, toolchain JDK 21
├── gradlew / gradlew.bat    # Gradle Wrapper 실행 스크립트
├── gradle/wrapper/          # gradle-wrapper.jar, gradle-wrapper.properties (Gradle 8.10.2)
├── src/
│   ├── main/java/ai/genesislab/calculator/Calculator.java
│   └── test/java/ai/genesislab/calculator/
│       ├── CalculatorTest.java             # 단위 테스트
│       └── CalculatorIntegrationTest.java  # 통합 테스트
└── docs/
    ├── CONVENTIONS.md       # (본 문서)
    └── calculator.md        # 계산기 모듈 문서
```

- 빌드 도구: **Gradle (Kotlin DSL)** — `build.gradle.kts`, `settings.gradle.kts`, Wrapper 포함.
- Java 소스/테스트 디렉토리: 표준 레이아웃 존재(`src/main/java`, `src/test/java`).
- 테스트 프레임워크: **JUnit 5 (Jupiter)** 구성 완료.

---

## 2. 빌드 도구 (사실)

- **Gradle Kotlin DSL** (`build.gradle.kts`). 적용 플러그인: `` `java-library` ``.
- 좌표: `group = "ai.genesislab"`, `version = "0.1.0"`. `settings.gradle.kts`의 `rootProject.name = "calculator"`.
- **Gradle Wrapper 버전: 8.10.2** (`gradle/wrapper/gradle-wrapper.properties`). 항상 `./gradlew`로 실행한다(로컬 Gradle 직접 호출 금지).
- 저장소: `mavenCentral()`.
- **Java toolchain: JDK 21** (`languageVersion = JavaLanguageVersion.of(21)`).
  - 빌드 스크립트 주석에 근거가 명시돼 있음: CONVENTIONS는 JDK 23+를 타깃으로 제안했으나, 재현 가능한 LTS 빌드를 위해 21을 선택. toolchain 값만 바꾸면 다른 버전으로 전환 가능.
- 테스트 태스크: `useJUnitPlatform()`, `testLogging`에 `passed/skipped/failed` 이벤트 출력.

### 2-1. 외부 의존성 추가 위치 (재사용 핵심)

`build.gradle.kts`의 `dependencies { }` 블록에 추가한다. 현재 내용:

```kotlin
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

- **런타임/구현 의존성**(라이브러리 사용자에게 노출): `api(...)` 또는 `implementation(...)`.
  - `java-library` 플러그인을 쓰므로, 의존성이 public API 시그니처에 노출되면 `api`, 내부 구현에만 쓰이면 `implementation`을 쓴다.
- **테스트 전용 의존성**: `testImplementation(...)`.
- JUnit은 **BOM(platform)** 으로 버전을 한 곳에서 관리하고 개별 아티팩트는 버전 없이 선언한다. 같은 패턴을 신규 의존성에도 권장.

---

## 3. 디렉토리 레이아웃 (사실 — 표준 Gradle/Maven 레이아웃)

```
src/
├── main/java/<package>/...        # 기능(라이브러리) 코드
└── test/java/<package>/...        # 테스트 코드 (main과 동일 패키지 미러링)
```

- 단위·통합 테스트 모두 `src/test/java`의 **동일 패키지**에 둔다. 단위/통합 구분은 별도 source set이 아니라 **클래스 명명**으로 한다(5절).
- 규모가 커지면 Gradle source set 분리(`src/integrationTest/java`)를 고려할 수 있으나, 현재 범위에서는 사용하지 않음.

---

## 4. 네이밍 · 패키지 · 레이어 규칙 (사실)

- **루트 패키지**: 역방향 도메인 표기 `ai.genesislab.<module>`. 현재 구현: `ai.genesislab.calculator`. 신규 모듈은 `ai.genesislab.<module>` 형태를 따른다(예: 암호화 모듈 → `ai.genesislab.crypto` 같은 단일 모듈 패키지).
- **클래스**: PascalCase. 순수 함수/상태 없는 모음은 `final class` + private 생성자(인스턴스화 방지)로 구현. 예: `Calculator`는 `private Calculator()`에서 `AssertionError`를 던진다.
- **메서드/변수**: camelCase. 요구사항 용어를 그대로 메서드명에 사용(`add`, `subtract`, `divide`, `power`, `modulo`).
- **상수**: UPPER_SNAKE_CASE. 예: `DIVIDE_BY_ZERO_MESSAGE`. 사용자가 검증할 수 있는 예외 메시지는 `public static final String` 상수로 노출한다.
- **레이어 구분**: CLI/HTTP 없는 **순수 함수 라이브러리** 단일 레이어. 외부 I/O·가변 상태를 두지 않는다.
- **예외 처리 패턴(재사용 후보)**:
  - 입력 검증 실패는 표준 예외를 던진다. 0 나누기는 `ArithmeticException`을 명시적으로 throw하고(언어 기본 Infinity/NaN에 의존하지 않음), 메시지는 위 상수를 사용한다.
  - 메서드는 부수효과 없는 결정적 출력. null 가능 입력이 생기면 동일하게 명시적 검증 후 표준 예외(`IllegalArgumentException`/`NullPointerException`)로 통일한다.

---

## 5. 테스트 위치 · 명명 (사실)

- **위치**: `src/test/java`, 대상 클래스와 동일 패키지로 미러링.
- **프레임워크**: **JUnit 5 (Jupiter)**, BOM `org.junit:junit-bom:5.11.3`. (AssertJ 등 보조 라이브러리는 현재 미사용.)
- **명명**(현재 구현 기준):
  - 단위 테스트 클래스: `<대상>Test` (예: `CalculatorTest`).
  - 통합 테스트 클래스: `<대상>IntegrationTest` (예: `CalculatorIntegrationTest`).
  - 테스트 메서드: 동작 설명형 `대상_상황_기대결과` (예: `divide_byZero_throwsArithmeticException`).
- **관측된 테스트 작성 패턴(재사용 후보)**:
  - 한글 `@DisplayName`을 클래스·메서드에 병행.
  - 연산 그룹별 `@Nested` 클래스로 구조화(`Add`, `Divide` 등).
  - 다중 입력은 `@ParameterizedTest` + `@CsvSource`로 표 기반 검증.
  - 실수 비교는 `private static final double DELTA = 1e-9` 허용 오차 사용.
  - 예외 검증은 `assertThrows`로 타입과 `getMessage()`(상수 대조)까지 확인.
  - 통합 테스트는 한 연산의 출력이 다음 연산 입력으로 흐르는 시나리오를 검증.

---

## 6. 코드 스타일 (사실)

- 들여쓰기 4 스페이스, 한 줄 120자 내.
- 순수 함수 지향: 입력에 대한 결정적 출력, 부수효과·가변 상태 지양.
- public API에는 Javadoc(파라미터 `@param`·반환 `@return`·예외 `@throws`) 작성. 현재 `Calculator`가 이 수준을 충족.
- import 와일드카드(`*`) 지양, 명시적·정적 import 사용.
- 포매터: IDE 기본(IntelliJ). Spotless 등 강제 포매터는 현 시점 미구성.

---

## 7. 산출물 위치 규약 (OS.md 준수)

| 종류 | 위치 |
|---|---|
| 구조·컨벤션 문서 | `docs/CONVENTIONS.md` |
| 일반 문서 | `docs/` (예: `docs/calculator.md`) |
| HTTP/API 문서 | `docs/http/` (현재 모듈들은 HTTP 없음 → 미사용) |
| 테스트 | `src/test/java` (본 문서 5절) |
