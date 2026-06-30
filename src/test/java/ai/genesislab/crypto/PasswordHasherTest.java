package ai.genesislab.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.genesislab.testutil.UtilityClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link PasswordHasher} 단위 테스트.
 *
 * <p>해싱·검증의 정상·경계·예외 케이스를 검증하며, BCrypt 72바이트 한계 동작을 테스트로 고정한다.</p>
 */
@DisplayName("PasswordHasher 단위 테스트")
class PasswordHasherTest {

    @Nested
    @DisplayName("hash(rawPassword) — 해싱")
    class Hash {

        @Test
        @DisplayName("정상: BCrypt 해시 문자열을 반환한다($2 접두사, cost 내장)")
        void hash_returnsBcryptString() {
            String hash = PasswordHasher.hash("my-password");

            assertTrue(hash.startsWith("$2"), "BCrypt 해시는 $2x$ 형태로 시작한다: " + hash);
            assertTrue(hash.contains("$10$"), "cost factor 10이 내장되어야 한다: " + hash);
            assertEquals(60, hash.length(), "BCrypt 해시 문자열 길이는 60이다");
        }

        @Test
        @DisplayName("경계: 같은 비밀번호도 salt가 달라 매번 다른 해시가 나온다")
        void hash_samePasswordTwice_producesDifferentHashes() {
            String first = PasswordHasher.hash("same-password");
            String second = PasswordHasher.hash("same-password");

            assertNotEquals(first, second);
            // 그래도 둘 다 원래 비밀번호와 일치해야 한다.
            assertTrue(PasswordHasher.matches("same-password", first));
            assertTrue(PasswordHasher.matches("same-password", second));
        }

        @Test
        @DisplayName("예외: null이면 IllegalArgumentException")
        void hash_null_throwsIllegalArgument() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.hash(null));
            assertEquals(PasswordHasher.NULL_RAW_PASSWORD_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: 빈 문자열이면 IllegalArgumentException")
        void hash_empty_throwsIllegalArgument() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.hash(""));
            assertEquals(PasswordHasher.EMPTY_RAW_PASSWORD_MESSAGE, ex.getMessage());
        }
    }

    @Nested
    @DisplayName("matches(rawPassword, hash) — 검증")
    class Matches {

        @Test
        @DisplayName("정상: 올바른 비밀번호면 true")
        void matches_correctPassword_returnsTrue() {
            String hash = PasswordHasher.hash("correct-horse");

            assertTrue(PasswordHasher.matches("correct-horse", hash));
        }

        @Test
        @DisplayName("정상: 틀린 비밀번호면 false")
        void matches_wrongPassword_returnsFalse() {
            String hash = PasswordHasher.hash("correct-horse");

            assertFalse(PasswordHasher.matches("wrong-horse", hash));
        }

        @ParameterizedTest(name = "잘못된 해시 포맷: \"{0}\" → false")
        @DisplayName("정의된 동작: 잘못된 해시 포맷은 예외 대신 false")
        @ValueSource(strings = {"not-a-hash", "", "$2a$broken", "plain text"})
        void matches_malformedHash_returnsFalse(String malformedHash) {
            assertFalse(PasswordHasher.matches("any-password", malformedHash));
        }

        @Test
        @DisplayName("예외: 평문이 null이면 IllegalArgumentException")
        void matches_nullRawPassword_throwsIllegalArgument() {
            String hash = PasswordHasher.hash("pw");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.matches(null, hash));
            assertEquals(PasswordHasher.NULL_RAW_PASSWORD_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: 평문이 빈 문자열이면 IllegalArgumentException")
        void matches_emptyRawPassword_throwsIllegalArgument() {
            String hash = PasswordHasher.hash("pw");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.matches("", hash));
            assertEquals(PasswordHasher.EMPTY_RAW_PASSWORD_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: 해시가 null이면 IllegalArgumentException")
        void matches_nullHash_throwsIllegalArgument() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.matches("pw", null));
            assertEquals(PasswordHasher.NULL_HASH_MESSAGE, ex.getMessage());
        }
    }

    @Nested
    @DisplayName("BCrypt 72바이트 한계 — 동작 고정")
    class LengthBoundary {

        @Test
        @DisplayName("경계: 한계 이내(71바이트)의 긴 비밀번호는 정상 해싱·검증된다")
        void hash_within72ByteLimit_works() {
            String longPassword = "a".repeat(71); // 71 ASCII bytes < 72

            String hash = PasswordHasher.hash(longPassword);

            assertTrue(PasswordHasher.matches(longPassword, hash));
        }

        @Test
        @DisplayName("예외: hash()에서 72바이트 초과면 IllegalArgumentException(메시지 상수 대조, 조용한 절단 금지)")
        void hash_over72ByteLimit_throwsIllegalArgument() {
            String tooLong = "a".repeat(100); // 100 ASCII bytes > 72

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.hash(tooLong));
            assertEquals(PasswordHasher.PASSWORD_TOO_LONG_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("결함#2 계약 대칭: matches()도 72바이트 초과면 동일 IllegalArgumentException(조용한 false 금지)")
        void matches_over72ByteLimit_throwsIllegalArgument() {
            // hash()에서 예외인 입력이 matches()에서 조용히 false가 되면 비대칭 계약 결함이다.
            String validHash = PasswordHasher.hash("a".repeat(71)); // 한계 이내의 정상 해시
            String tooLong = "a".repeat(100);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.matches(tooLong, validHash));
            assertEquals(PasswordHasher.PASSWORD_TOO_LONG_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("경계: 72바이트는 멀티바이트 문자 기준이다(문자 수가 아닌 UTF-8 바이트 수)")
        void length_isMeasuredInUtf8Bytes_notCharacters() {
            // '가'는 UTF-8 3바이트 → 24자면 72바이트(정상), 25자면 75바이트(초과).
            String exactly72Bytes = "가".repeat(24);
            String over72Bytes = "가".repeat(25);

            // 72바이트 정확히: 정상 해싱·검증된다.
            String hash = PasswordHasher.hash(exactly72Bytes);
            assertTrue(PasswordHasher.matches(exactly72Bytes, hash));

            // 75바이트: hash()·matches() 모두 동일하게 거부된다.
            IllegalArgumentException hashEx = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.hash(over72Bytes));
            assertEquals(PasswordHasher.PASSWORD_TOO_LONG_MESSAGE, hashEx.getMessage());
            IllegalArgumentException matchesEx = assertThrows(IllegalArgumentException.class,
                    () -> PasswordHasher.matches(over72Bytes, hash));
            assertEquals(PasswordHasher.PASSWORD_TOO_LONG_MESSAGE, matchesEx.getMessage());
        }
    }

    @Test
    @DisplayName("설계: 유틸리티 클래스는 인스턴스화할 수 없다")
    void passwordHasher_cannotBeInstantiated() throws Exception {
        UtilityClasses.assertNotInstantiable(PasswordHasher.class);
    }
}
