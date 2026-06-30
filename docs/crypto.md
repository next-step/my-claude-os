# Crypto 모듈 사용 문서

이 문서는 암호화 라이브러리 모듈의 사용법·API·설계 제약을 설명한다.
OS.md의 [4단계] 산출물이며, 코드와 어긋나면 본 문서를 현실에 맞게 갱신한다.

> 상태(2026-06-27): 단위/통합 테스트 91/91 통과(green) 기준으로 작성됨.
>
> **HTTP API 없음(N/A)**: 본 모듈은 순수 라이브러리(JVM 메서드 호출)이므로 엔드포인트가 없다.
> 따라서 `docs/http/` 문서는 작성하지 않는다.

---

## 1. 개요

- **역할**: 두 가지 암호화 기능을 제공하는 라이브러리.
  - **양방향(대칭, 가역)**: AES-256/GCM — 평문을 암호화/복호화한다. 기밀성과 무결성(변조 탐지)을 동시에 보장하는 AEAD.
  - **단방향(비가역)**: BCrypt — 비밀번호를 해싱하고 검증한다(원문 복원 불가).
- **패키지**: `ai.genesislab.crypto`
- **위치**:
  - `src/main/java/ai/genesislab/crypto/AesGcmCipher.java` (양방향)
  - `src/main/java/ai/genesislab/crypto/PasswordHasher.java` (단방향)
  - `src/main/java/ai/genesislab/crypto/CryptoException.java` (공통 비검사 예외)
- **특성**: CLI/HTTP 없는 순수 라이브러리 레이어. 두 유틸 클래스 모두 상태가 없는 정적 메서드 모음(`final` + private 생성자, 인스턴스화 시 `AssertionError`).

---

## 2. 공개 API

### 2-1. `AesGcmCipher` — 양방향(AES-256/GCM)

모든 메서드는 정적 메서드다.

| 메서드 | 시그니처 | 반환 | 예외 |
|---|---|---|---|
| `generateKey` | `static SecretKey generateKey()` | 새 256-bit AES `SecretKey` | `CryptoException` (AES 알고리즘 미가용 시) |
| `encrypt` | `static String encrypt(String plaintext, SecretKey key)` | `Base64(IV ‖ 암호문+태그)` self-contained 문자열 | `IllegalArgumentException`(null 입력), `CryptoException`(암호화 실패) |
| `decrypt` | `static String decrypt(String ciphertext, SecretKey key)` | 복호화된 UTF-8 평문 | `IllegalArgumentException`(null 입력), `CryptoException`(포맷 오류 또는 인증 실패) |

- `encrypt`는 **매 호출마다 `SecureRandom`으로 새 12바이트 IV**를 만들어 암호문 앞에 prepend한다. 따라서 같은 평문·같은 키라도 호출마다 다른 출력이 나온다. 빈 평문(`""`)도 암호화 가능하다.
- `decrypt`는 별도의 IV 전달 없이 `encrypt` 출력 문자열만으로 복호화한다(IV가 페이로드에 포함되어 self-contained).

#### 공개 상수

| 상수 | 값 | 의미 |
|---|---|---|
| `TRANSFORMATION` | `"AES/GCM/NoPadding"` | 암호화 변환 문자열 |
| `KEY_ALGORITHM` | `"AES"` | 키 알고리즘 |
| `KEY_SIZE_BITS` | `256` | 키 길이(bit) |
| `IV_LENGTH_BYTES` | `12` | IV 길이(byte, GCM 권장값) |
| `GCM_TAG_LENGTH_BITS` | `128` | GCM 인증 태그 길이(bit) |
| `GCM_TAG_LENGTH_BYTES` | `16` | GCM 인증 태그 길이(byte) |
| `NULL_PLAINTEXT_MESSAGE` | `"plaintext must not be null."` | 예외 메시지 |
| `NULL_CIPHERTEXT_MESSAGE` | `"ciphertext must not be null."` | 예외 메시지 |
| `NULL_KEY_MESSAGE` | `"key must not be null."` | 예외 메시지 |
| `MALFORMED_CIPHERTEXT_MESSAGE` | `"ciphertext is malformed: expected Base64 string with a 12-byte IV prefix and a 16-byte GCM tag."` | 포맷 오류 메시지 |

> 정상 암호문의 최소 길이는 **IV(12) + GCM 태그(16) = 28바이트**다. 빈 평문을 암호화해도 28바이트가 나온다.

### 2-2. `PasswordHasher` — 단방향(BCrypt)

모든 메서드는 정적 메서드다.

| 메서드 | 시그니처 | 반환 | 예외 |
|---|---|---|---|
| `hash` | `static String hash(String rawPassword)` | salt·cost 내장 BCrypt 해시(60자, 예: `$2a$10$...`) | `IllegalArgumentException`(null/빈 문자열/72바이트 초과) |
| `matches` | `static boolean matches(String rawPassword, String hash)` | 일치 `true` / 불일치·해시 포맷 오류 `false` | `IllegalArgumentException`(`rawPassword` null/빈/72바이트 초과, `hash` null) |

- `hash`는 **매 호출마다 무작위 salt**를 생성해 결과에 내장한다. cost factor는 `10`.
- `matches`는 해시에 내장된 salt·cost를 사용하므로 **salt를 따로 전달할 필요가 없다**.

#### 공개 상수

| 상수 | 값 | 의미 |
|---|---|---|
| `COST_FACTOR` | `10` | BCrypt cost factor |
| `MAX_PASSWORD_BYTES` | `72` | BCrypt가 실제 사용하는 비밀번호 최대 길이(UTF-8 바이트) |
| `NULL_RAW_PASSWORD_MESSAGE` | `"rawPassword must not be null."` | 예외 메시지 |
| `EMPTY_RAW_PASSWORD_MESSAGE` | `"rawPassword must not be empty."` | 예외 메시지 |
| `PASSWORD_TOO_LONG_MESSAGE` | `"rawPassword must not exceed 72 bytes when UTF-8 encoded."` | 72바이트 초과 메시지 |
| `NULL_HASH_MESSAGE` | `"hash must not be null."` | 예외 메시지 |

### 2-3. `CryptoException`

- `class CryptoException extends RuntimeException` — 암호화 계열 공통 **비검사 예외**.
- JDK 암호화 API의 검사 예외(`GeneralSecurityException` 계열)를 사용자에게 강제하지 않도록 일관되게 래핑한다. **원인(cause)은 항상 보존**한다.
- 생성자: `CryptoException(String message)`, `CryptoException(String message, Throwable cause)`.
- GCM 인증 태그 검증 실패 시 원인으로 `javax.crypto.AEADBadTagException`을 담는다. 호출자는 `getCause()`로 실패 원인을 구분할 수 있다.

---

## 3. 사용 예시 (Java)

### 양방향 암호화 왕복 (키 생성 → 암호화 → 복호화)

```java
import ai.genesislab.crypto.AesGcmCipher;
import javax.crypto.SecretKey;

SecretKey key = AesGcmCipher.generateKey();          // 256-bit AES 키 생성

String ciphertext = AesGcmCipher.encrypt("hello", key); // Base64(IV‖암호문+태그)
String plaintext  = AesGcmCipher.decrypt(ciphertext, key); // "hello"

// 같은 평문·같은 키라도 매번 다른 암호문이 나온다(랜덤 IV):
String c1 = AesGcmCipher.encrypt("hello", key);
String c2 = AesGcmCipher.encrypt("hello", key);
// c1 != c2 이지만 둘 다 "hello"로 복호화된다.
```

### 비밀번호 해싱 및 검증 (hash → matches)

```java
import ai.genesislab.crypto.PasswordHasher;

String hash = PasswordHasher.hash("s3cr3t!");         // "$2a$10$..." (60자, salt 내장)

boolean ok   = PasswordHasher.matches("s3cr3t!", hash);  // true
boolean fail = PasswordHasher.matches("wrong", hash);    // false
```

### 예외 처리

```java
import ai.genesislab.crypto.AesGcmCipher;
import ai.genesislab.crypto.CryptoException;
import javax.crypto.AEADBadTagException;

try {
    AesGcmCipher.decrypt(tampered, key);
} catch (CryptoException e) {
    if (e.getCause() instanceof AEADBadTagException) {
        // 인증 실패: 변조됐거나 잘못된 키
    } else if (AesGcmCipher.MALFORMED_CIPHERTEXT_MESSAGE.equals(e.getMessage())) {
        // 포맷 오류: Base64 손상 또는 28바이트 미만
    }
}
```

---

## 4. 에러 계약 (중요)

### 4-1. AES — "포맷 오류(malformed)" vs "인증 실패(변조)"의 명확한 구분

두 실패는 **의미가 다르며 코드에서 구분된다**. 호출자는 이 구분에 의존해 분기할 수 있다.

| 입력 상황 | 결과 | 구분 기준 |
|---|---|---|
| `plaintext`/`ciphertext`/`key` == null | `IllegalArgumentException`(해당 상수 메시지) | 입력 계약 위반 |
| Base64 디코드 실패(손상) | `CryptoException` + `MALFORMED_CIPHERTEXT_MESSAGE` | **포맷 오류** |
| 디코드 길이 < 28바이트(IV12+태그16 미만) | `CryptoException` + `MALFORMED_CIPHERTEXT_MESSAGE` | **포맷 오류** |
| 변조됐거나 잘못된 키 | `CryptoException`(인증 실패 메시지, cause = `AEADBadTagException`) | **인증 실패** |

> **왜 길이 검사를 먼저 하는가**: 28바이트 미만이면 태그를 담을 수조차 없는 구조적 손상이다.
> 이를 그대로 `doFinal`에 넘기면 `AEADBadTagException`(=변조)으로 던져져 "포맷 오류"와 "변조"가 혼동된다.
> 그래서 길이를 먼저 검사해 **포맷 오류(MALFORMED)** 로 분류하고, 진짜 인증 실패만 cause로 구분되게 한다.

### 4-2. BCrypt — 72바이트 한계의 대칭 처리

- **72바이트는 문자 수가 아니라 UTF-8 인코딩 바이트 길이**다. 멀티바이트 문자(한글·이모지 등)는 한 글자가 여러 바이트이므로, 72자 미만이라도 72바이트를 초과할 수 있다.
- **`hash`와 `matches`는 입력 검증이 대칭이다.** `rawPassword`의 null/빈 문자열/72바이트 초과는 양쪽 모두 동일하게 `IllegalArgumentException`으로 거부한다.
  - 특히 72바이트 초과 시 `matches`는 **조용히 `false`를 반환하지 않고** `IllegalArgumentException`(`PASSWORD_TOO_LONG_MESSAGE`)을 던진다. 조용한 절단으로 인한 보안 사고를 막기 위함이다.
- `matches`의 `false`는 **오직 두 경우**로 한정된다: (1) 비밀번호 불일치, (2) `hash` 문자열 포맷이 깨진 경우(파싱 불가). 즉 깨진 해시 포맷은 예외가 아니라 `false`다.

| 입력 상황 | `hash` | `matches` |
|---|---|---|
| `rawPassword` == null | `IllegalArgumentException` | `IllegalArgumentException` |
| `rawPassword` == "" | `IllegalArgumentException` | `IllegalArgumentException` |
| `rawPassword` UTF-8 > 72바이트 | `IllegalArgumentException` | `IllegalArgumentException` (대칭) |
| `hash` == null | — | `IllegalArgumentException` |
| 깨진 `hash` 포맷 | — | `false` |
| 비밀번호 불일치 | — | `false` |

---

## 5. 주요 설계 결정 · 제약

- **AES-GCM(AEAD) 선택**: 기밀성과 무결성(변조 탐지)을 동시에 제공한다. 복호화 시 인증 태그가 검증되므로, 변조된 암호문은 평문을 내놓지 않고 인증 실패로 거부된다.
- **랜덤 IV + self-contained 포맷**: 매 암호화마다 새 12바이트 IV를 생성하므로 같은 평문도 매번 다른 암호문이 된다. IV를 암호문 앞에 prepend(`Base64(IV‖암호문+태그)`)해 별도 IV 전달이 필요 없다.
- **키 관리는 호출자 책임**: 모듈은 암복호화만 담당하고 `SecretKey`는 호출자가 주입한다. `generateKey()`는 키 생성 유틸/테스트용이며, **키의 저장·재사용·로테이션은 호출자 책임**이다.
- **BCrypt 단방향 + salt 내장**: 해시에 salt와 cost가 내장돼 `matches`가 salt 없이 검증한다. cost factor는 `10`(업계 기본값이자 테스트 속도 고려). 72바이트 한계는 UTF-8 바이트 기준으로 직접 검증해 조용한 절단을 막는다.
- **에러 계약의 일관성**: 입력 계약 위반은 `IllegalArgumentException`(공개 상수 메시지), 운영 실패는 `CryptoException`(원인 보존)으로 통일한다. 모든 예외 메시지는 `public static final` 상수로 노출해 호출 측에서 대조 가능.
- **정적 유틸 클래스**: 두 클래스 모두 상태가 없으므로 `final` + private 생성자로 인스턴스화를 금지한다(`AssertionError`).

---

## 6. 빌드 · 의존성 · 테스트 실행

- 빌드 도구: **Gradle (Kotlin DSL)**, 플러그인 `java-library`, **JDK 21 toolchain**.
- 의존성:
  - **양방향(`AesGcmCipher`)**: JDK 내장 `javax.crypto`만 사용 — **외부 의존성 0**.
  - **단방향(`PasswordHasher`)**: `at.favre.lib:bcrypt:0.10.2`. `java-library`에서 BCrypt 타입을 public 시그니처에 노출하지 않으므로 `implementation` 스코프로 선언(입출력은 모두 `String`/`boolean`).
- 테스트 위치: `src/test/java/ai/genesislab/crypto/`
  - 단위: `AesGcmCipherTest`, `PasswordHasherTest`
  - 통합: `AesGcmCipherIntegrationTest`, `PasswordHasherIntegrationTest`

### 실행

```bash
./gradlew test
```

### JAVA_HOME 주의사항

- 빌드는 Gradle **toolchain으로 Java 21(Corretto 21, LTS)** 을 타깃한다(`build.gradle.kts`). 로컬 `JAVA_HOME`은 Corretto 21 이상을 권장한다(상세는 `docs/calculator.md` 4절 참고).

---

## 7. 관련 문서

- 저장소 구조·네이밍·테스트·빌드 규칙: [`docs/CONVENTIONS.md`](./CONVENTIONS.md)
- 계산기 모듈 문서(동일 스타일): [`docs/calculator.md`](./calculator.md)
- 개발 파이프라인 표준: [`OS.md`](../OS.md)
