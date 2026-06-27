# OS 진행 상태 (state)

> 오케스트레이터(`/os`)가 단계 전이마다 갱신한다. 이 파일이 있으면 진행 중인 작업이다.

stage: done         # 1 | 2 | 3 | 4 | done
started_at: 2026-06-27 15:33:05
updated_at: 2026-06-27 16:15:00

## 요구사항 (확정된 계약서 — 각 에이전트에 전달)
- 한 줄 요약: Java 라이브러리 암호화 모듈 — 양방향(대칭, 가역) + 단방향(비밀번호 해싱) 제공
- 입력/출력:
  - 양방향: `encrypt(plaintext, SecretKey) -> ciphertext`, `decrypt(ciphertext, SecretKey) -> plaintext`. IV는 암호문에 prepend해 자체 포함(self-contained). `generateKey()` 헬퍼로 256-bit 키 생성.
  - 단방향: `hash(rawPassword) -> hash 문자열`, `matches(rawPassword, hash) -> boolean`. salt는 해시에 내장.
- 경계 조건: 빈 문자열/긴 평문, 멀티바이트(UTF-8), 잘못된 키 길이, 잘못된 IV. BCrypt 72바이트 입력 한계 인지.
- 예외 케이스:
  - 양방향: 잘못된 키/변조된 암호문/태그 검증 실패 → 인증 실패 예외(AEADBadTagException 계열) 명확히 throw. null 입력 방어.
  - 단방향: null/빈 입력 방어. matches는 잘못된 해시 포맷에 예외 또는 false 명확 정의.

## 확정된 설계 결정 (결정 게이트 ① 산출 — 사람 승인됨)
> 회색지대 결정을 추천 기본값과 함께 사람이 confirm/override한 결과. 각 에이전트 위임 시 함께 전달한다.
- 공개 API 계약(시그니처·반환·에러 처리): 양방향=encrypt/decrypt/generateKey, 단방향=hash/matches. 복호화/검증 실패는 예외 또는 false로 명확히 구분(테스트로 고정). 바이트↔문자열 변환은 Base64 + UTF-8.
- 도메인 정확성(알고리즘·파라미터): 양방향 `AES/GCM/NoPadding`, 키 256-bit, IV 12바이트(랜덤, SecureRandom), GCM 태그 128-bit. 단방향 BCrypt(cost factor 기본 10~12).
- 환경/빌드(빌드도구·런타임/toolchain 버전): 기존 Gradle(Kotlin DSL) + Java. 양방향은 JDK javax.crypto 내장(의존성 0). 단방향은 외부 라이브러리 `at.favre.lib:bcrypt` 의존성 1개 추가.
- 비기능 요건(스레드 안전성·성능·의존성): 키 주입은 호출자 책임(모듈은 암복호화만). generateKey()는 테스트·유틸용. BCrypt 의존성 1개 추가 승인됨.
- AI 자체 결정(묻지 않고 정한 항목 로그): 패키지명/클래스명/테스트 파일 명명/내부 구현 세부(IV prepend 방식, Base64 인코딩 선택 등)는 1단계 컨벤션 따라 os-developer가 결정.

## 게이트 통과 기록
- [x] 결정 게이트 ① — 사람 (2단계 위임 전, 설계 결정 승인) — AskUserQuestion 3건 확정
- [x] 리뷰 게이트 — AI (1차 블로킹 2건 발견→2단계 수정→재검증 green→블로킹 해소 확인. 풀 재리뷰는 변경 소규모로 생략)
- [x] 수용 게이트 ② — 사람 (done 전, 의도 일치·누락 없음 확인) — 사용자 "수용 — done 처리"

## DoD 체크리스트 (OS.md 기준)
### 1단계 — 코드베이스·컨벤션 파악
- [x] CONVENTIONS 문서가 현재 코드와 일치
- [x] 재사용 가능한 기존 자산 식별됨
### 2단계 — 분석·개발·테스트 작성
- [x] 모든 요구사항이 코드로 구현됨 (리뷰 게이트 후 정확성 결함 2건 수정 중)
- [x] 각 요구사항에 대응하는 단위·통합 테스트 존재
- [x] 기존 자산 재사용(또는 불가 사유 명확)
### 3단계 — 검증 루프
- [x] 모든 단위·통합 테스트 green (재검증 91/91)
- [x] skip/가짜 통과 테스트 없음
### 4단계 — 문서화
- [x] 새 기능/변경점이 docs에 반영 (docs/crypto.md)
- [x] API 변경이 HTTP 문서에 반영 (라이브러리 모듈 → HTTP 없음 N/A)

## 산출물 경로
- CONVENTIONS: docs/CONVENTIONS.md (갱신됨)
- 코드: src/main/java/ai/genesislab/crypto/{AesGcmCipher,PasswordHasher,CryptoException}.java, build.gradle.kts(bcrypt 의존성)
- 테스트: src/test/java/ai/genesislab/crypto/{AesGcmCipherTest,PasswordHasherTest,AesGcmCipherIntegrationTest,PasswordHasherIntegrationTest}.java, src/test/java/ai/genesislab/testutil/UtilityClasses.java
- 문서: docs/crypto.md (HTTP 문서는 API 부재로 N/A)

## 단계별 로그
- [1단계] 완료. docs/CONVENTIONS.md를 "제안"→"현재 코드 사실" 기준으로 정정. 재사용 자산: BCrypt 의존성은 build.gradle.kts dependencies에 implementation("at.favre.lib:bcrypt:0.10.2"), AES/GCM은 javax.crypto 내장(의존성0). 패키지 ai.genesislab.crypto 권장, 단위<X>Test/통합<X>IntegrationTest, 예외 메시지 상수 패턴. ⚠️toolchain 불일치(IDE 23/24 vs Gradle toolchain 21 vs 구동JVM 11) 발견 — 현 빌드는 21로 정상. 결정: JDK 21 빌드 유지로 진행(로그만).
- [2단계] 완료. ai.genesislab.crypto 패키지에 AesGcmCipher(AES-256-GCM, IV prepend+Base64, generateKey/encrypt/decrypt), PasswordHasher(BCrypt cost10, hash/matches), 공통 CryptoException 구현. build.gradle.kts에 implementation("at.favre.lib:bcrypt:0.10.2") 추가. 단위28+통합10=38 테스트. null→IllegalArgumentException, 복호화 실패→CryptoException(cause AEADBadTagException), matches 잘못된 포맷→false, BCrypt 72B 초과→예외. developer 1회 실행 BUILD SUCCESSFUL(38 green, skip0).
- [3단계] 1차 완료. ./gradlew clean test (JAVA_HOME=corretto-21) → BUILD SUCCESSFUL, 85 passed/0 failed/0 skipped(calculator 40 + crypto 45). bcrypt 0.10.2 + 전이 bytes 1.5.0 mavenCentral 정상 resolve. 가짜통과/skip 패턴 grep 0건. (리뷰 게이트 블로킹으로 수정 후 재검증 예정)
- [리뷰 게이트] 1차: /code-review high (멀티에이전트, 8 finder/22 후보/검증). 🔴블로킹 2건 — (1)AesGcmCipher.decrypt 길이가드가 IV(12)만 검사, 태그(16) 누락 → 12~27B 손상 암호문이 변조로 오분류(MALFORMED여야 함). (2)PasswordHasher.matches가 IllegalArgumentException 전체를 false로 흡수 → 72B초과 비번이 hash는 예외/matches는 조용히 불일치(비대칭). 컨벤션 2건(72B 메시지 상수 부재 §4, 예외테스트 getMessage 미대조 §5). 경미 효율2(copyOfRange/IV이중복사)+죽은단언2+테스트중복2. → 2단계 재위임.
  수정(2단계 재): GCM_TAG_LENGTH_BYTES=16 도입, 가드 28B 기준화→12~27B MALFORMED 분류. offset doFinal/GCMParameterSpec. PasswordHasher MAX_PASSWORD_BYTES=72/PASSWORD_TOO_LONG_MESSAGE 상수, hash·matches 72B UTF-8 검증 대칭화, matches catch를 해시포맷 오류로 한정. testutil/UtilityClasses.assertNotInstantiable 헬퍼 신설(crypto 2곳 재사용, Calculator 미변경). 죽은 단언 제거. 회귀 테스트 추가.
  재검증(3단계 재): clean test 91/91 green, skip0. 블로킹 회귀 테스트 실행·통과 확인. → 블로킹 해소, 게이트 통과(풀 재리뷰 생략).
- [4단계] 완료. docs/crypto.md 신규 작성(개요·공개API표·사용예시·에러계약[malformed vs 인증실패, 72B UTF-8 대칭]·설계결정·빌드/의존성). 기존 docs/calculator.md 스타일 준수. HTTP API 없음→N/A. 코드/테스트 미변경.
