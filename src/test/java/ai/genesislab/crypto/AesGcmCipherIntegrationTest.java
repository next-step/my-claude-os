package ai.genesislab.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AesGcmCipher} 통합 테스트.
 *
 * <p>"키 생성 → 암호화 → 문자열 저장(직렬화) → 키 복원 → 복호화"의 실제 흐름을 검증한다.
 * 키를 Base64 문자열로 보관했다가 {@link SecretKeySpec}으로 복원하는, 운영에서 흔한 시나리오를 다룬다.</p>
 */
@DisplayName("AesGcmCipher 통합 테스트")
class AesGcmCipherIntegrationTest {

    @Test
    @DisplayName("키 생성→암호화→문자열 저장→키 복원→복호화 전체 경로가 원문을 복원한다")
    void fullPath_keyAndCiphertextSerialized_thenDecrypts() {
        // 1) 키 생성 후 외부 저장을 가정해 Base64 문자열로 직렬화한다.
        SecretKey originalKey = AesGcmCipher.generateKey();
        String storedKey = Base64.getEncoder().encodeToString(originalKey.getEncoded());

        // 2) 암호문을 만들고 문자열로 저장(전송)한다고 가정한다.
        String message = "주문번호 1004 / 금액 50,000원";
        String storedCiphertext = AesGcmCipher.encrypt(message, originalKey);

        // 3) 저장된 문자열들로부터 키와 암호문을 복원한다(다른 시점/프로세스 가정).
        byte[] keyBytes = Base64.getDecoder().decode(storedKey);
        SecretKey restoredKey = new SecretKeySpec(keyBytes, AesGcmCipher.KEY_ALGORITHM);

        // 4) 복원한 키로 복호화하면 원문과 일치한다.
        String decrypted = AesGcmCipher.decrypt(storedCiphertext, restoredKey);
        assertEquals(message, decrypted);
    }

    @Test
    @DisplayName("멀티바이트(한글/이모지) 평문도 왕복 후 원문과 동일하다")
    void fullPath_multiByteUtf8_roundTrips() {
        SecretKey key = AesGcmCipher.generateKey();
        String message = "안녕하세요 🌟 Crypto 모듈 — UTF-8 검증";

        String decrypted = AesGcmCipher.decrypt(AesGcmCipher.encrypt(message, key), key);

        // assertEquals(String)는 내부적으로 UTF-16 시퀀스를 비교하므로 멀티바이트 동일성까지 보장한다.
        // (직전의 Base64-of-UTF8 재비교는 decrypted에서 파생된 항상-참 단언이라 제거)
        assertEquals(message, decrypted);
    }

    @Test
    @DisplayName("여러 메시지를 한 키로 순차 암복호화해도 서로 간섭하지 않는다")
    void fullPath_multipleMessagesWithSameKey_independent() {
        SecretKey key = AesGcmCipher.generateKey();
        String[] messages = {"first", "second", "세 번째", ""};

        for (String message : messages) {
            String ciphertext = AesGcmCipher.encrypt(message, key);
            assertEquals(message, AesGcmCipher.decrypt(ciphertext, key));
        }
    }

    @Test
    @DisplayName("저장된 암호문이 전송 중 변조되면 복호화가 인증 실패로 거부된다")
    void fullPath_tamperedDuringTransit_isRejected() {
        SecretKey key = AesGcmCipher.generateKey();
        String ciphertext = AesGcmCipher.encrypt("민감정보", key);

        // 전송 중 한 바이트가 손상됐다고 가정한다.
        byte[] raw = Base64.getDecoder().decode(ciphertext);
        raw[AesGcmCipher.IV_LENGTH_BYTES] ^= 0x02; // 실제 암호문 첫 바이트 손상
        String corrupted = Base64.getEncoder().encodeToString(raw);

        // 통합 관점의 차별화된 가치: 변조본은 거부되지만, 같은 키로 손상되지 않은 원본은 여전히
        // 정상 복호화된다(전체 경로에서 변조 탐지가 정상 트래픽을 막지 않음을 확인).
        assertThrows(CryptoException.class, () -> AesGcmCipher.decrypt(corrupted, key));
        assertTrue(AesGcmCipher.decrypt(ciphertext, key).equals("민감정보"));
    }
}
