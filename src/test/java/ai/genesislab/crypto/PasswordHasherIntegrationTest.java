package ai.genesislab.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PasswordHasher} 통합 테스트.
 *
 * <p>"회원가입(hash) → 저장 → 로그인 검증(matches)"의 실제 인증 흐름을 검증한다.
 * 저장소는 사용자명→해시 맵으로 단순화한다(해시만 저장하고 평문은 보관하지 않는다).</p>
 */
@DisplayName("PasswordHasher 통합 테스트")
class PasswordHasherIntegrationTest {

    /** 사용자명 → 저장된 비밀번호 해시(평문은 저장하지 않는다). */
    private final Map<String, String> userStore = new HashMap<>();

    private void register(String username, String rawPassword) {
        userStore.put(username, PasswordHasher.hash(rawPassword));
    }

    private boolean login(String username, String rawPassword) {
        String storedHash = userStore.get(username);
        if (storedHash == null) {
            return false;
        }
        return PasswordHasher.matches(rawPassword, storedHash);
    }

    @Test
    @DisplayName("회원가입 후 올바른 비밀번호로 로그인하면 성공한다")
    void register_thenLoginWithCorrectPassword_succeeds() {
        register("alice", "S3cure!Pass");

        assertTrue(login("alice", "S3cure!Pass"));
    }

    @Test
    @DisplayName("틀린 비밀번호로 로그인하면 실패한다")
    void login_withWrongPassword_fails() {
        register("bob", "hunter2");

        assertFalse(login("bob", "hunter3"));
    }

    @Test
    @DisplayName("저장소에는 평문이 아닌 해시만 남고, 같은 비밀번호라도 사용자별 해시가 다르다")
    void store_keepsOnlyDistinctHashes_notPlaintext() {
        register("carol", "samePassword");
        register("dave", "samePassword");

        String carolHash = userStore.get("carol");
        String daveHash = userStore.get("dave");

        assertNotEquals("samePassword", carolHash); // 평문이 그대로 저장되지 않는다
        assertNotEquals(carolHash, daveHash);        // salt가 달라 해시도 다르다
        // 그래도 각자 자신의 비밀번호로 로그인 가능하다.
        assertTrue(login("carol", "samePassword"));
        assertTrue(login("dave", "samePassword"));
    }

    @Test
    @DisplayName("멀티바이트(한글) 비밀번호도 등록·로그인 흐름이 동작한다")
    void register_thenLogin_multiBytePassword() {
        register("eve", "비밀번호_가나다123!");

        assertTrue(login("eve", "비밀번호_가나다123!"));
        assertFalse(login("eve", "비밀번호_가나다124!"));
    }

    @Test
    @DisplayName("등록되지 않은 사용자는 로그인할 수 없다")
    void login_unknownUser_fails() {
        assertFalse(login("ghost", "whatever"));
    }
}
