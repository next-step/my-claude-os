package ai.genesislab.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256/GCM 기반 양방향(대칭, 가역) 암호화 유틸리티.
 *
 * <p>JDK 내장 {@code javax.crypto}만 사용하며 외부 의존성이 없다. 알고리즘은
 * {@code AES/GCM/NoPadding}, 키 길이 256-bit, IV 12바이트, GCM 인증 태그 128-bit이다.</p>
 *
 * <p>{@link #encrypt(String, SecretKey)}는 매 호출마다 {@link SecureRandom}으로 새 IV를
 * 생성하고, 그 IV를 암호문 앞에 prepend한 뒤 전체를 Base64로 인코딩해 반환한다(self-contained).
 * 따라서 {@link #decrypt(String, SecretKey)}는 별도의 IV 전달 없이 동일 문자열만으로 복호화한다.</p>
 *
 * <p>본 클래스는 상태가 없으므로 인스턴스화하지 않는다(정적 메서드 모음). 내부 {@link SecureRandom}은
 * 스레드 안전한 공유 인스턴스다.</p>
 */
public final class AesGcmCipher {

    /** 암호화 변환 문자열. */
    public static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** 키 알고리즘. */
    public static final String KEY_ALGORITHM = "AES";

    /** 키 길이(bit). */
    public static final int KEY_SIZE_BITS = 256;

    /** IV 길이(byte). GCM 권장값 12바이트. */
    public static final int IV_LENGTH_BYTES = 12;

    /** GCM 인증 태그 길이(bit). */
    public static final int GCM_TAG_LENGTH_BITS = 128;

    /** GCM 인증 태그 길이(byte). 정상 암호문은 항상 IV + 이 태그를 포함한다(빈 평문도 28바이트). */
    public static final int GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8;

    /** 평문이 null일 때의 예외 메시지. */
    public static final String NULL_PLAINTEXT_MESSAGE = "plaintext must not be null.";

    /** 암호문이 null일 때의 예외 메시지. */
    public static final String NULL_CIPHERTEXT_MESSAGE = "ciphertext must not be null.";

    /** 키가 null일 때의 예외 메시지. */
    public static final String NULL_KEY_MESSAGE = "key must not be null.";

    /** 암호문 형식이 잘못됐을 때의 예외 메시지. */
    public static final String MALFORMED_CIPHERTEXT_MESSAGE =
            "ciphertext is malformed: expected Base64 string with a 12-byte IV prefix and a 16-byte GCM tag.";

    /** 스레드 안전한 공유 난수원. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCipher() {
        // 유틸리티 클래스: 인스턴스화 방지.
        throw new AssertionError("No ai.genesislab.crypto.AesGcmCipher instances for you!");
    }

    /**
     * 256-bit AES 비밀키를 새로 생성한다(테스트·유틸용).
     *
     * @return 새로 생성된 256-bit {@link SecretKey}
     * @throws CryptoException AES 알고리즘을 사용할 수 없는 경우(원인 보존)
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGenerator.init(KEY_SIZE_BITS, SECURE_RANDOM);
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("Failed to generate AES key.", e);
        }
    }

    /**
     * 평문을 AES-256/GCM으로 암호화한다. 매 호출마다 새 랜덤 IV가 사용된다.
     *
     * <p>반환값은 {@code Base64(IV || ciphertext+tag)} 형태의 self-contained 문자열이다.</p>
     *
     * @param plaintext UTF-8로 인코딩할 평문(빈 문자열 허용, null 불가)
     * @param key       암호화 키(null 불가)
     * @return IV가 prepend된 Base64 인코딩 암호문
     * @throws IllegalArgumentException {@code plaintext} 또는 {@code key}가 null인 경우
     * @throws CryptoException          암호화에 실패한 경우(원인 보존)
     */
    public static String encrypt(String plaintext, SecretKey key) {
        if (plaintext == null) {
            throw new IllegalArgumentException(NULL_PLAINTEXT_MESSAGE);
        }
        if (key == null) {
            throw new IllegalArgumentException(NULL_KEY_MESSAGE);
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Failed to encrypt plaintext.", e);
        }
    }

    /**
     * {@link #encrypt(String, SecretKey)}가 생성한 문자열을 복호화한다.
     *
     * <p>Base64 디코드 후 앞 {@value #IV_LENGTH_BYTES}바이트를 IV로, 나머지를 암호문(+태그)으로
     * 분리해 복호화하고 UTF-8 평문을 반환한다. 디코드 결과가 최소 유효 길이
     * (IV {@value #IV_LENGTH_BYTES}바이트 + 태그 {@value #GCM_TAG_LENGTH_BYTES}바이트 = 28바이트)
     * 미만이면 구조적 손상으로 보아 인증 실패가 아닌 포맷 오류로 분류한다.</p>
     *
     * @param ciphertext {@link #encrypt(String, SecretKey)}가 반환한 Base64 문자열(null 불가)
     * @param key        복호화 키(null 불가)
     * @return 복호화된 UTF-8 평문
     * @throws IllegalArgumentException {@code ciphertext} 또는 {@code key}가 null인 경우
     * @throws CryptoException          Base64 디코드에 실패하거나 디코드 길이가 최소 유효 길이(28바이트) 미만이거나
     *                                  (메시지 {@link #MALFORMED_CIPHERTEXT_MESSAGE}),
     *                                  인증 태그 검증에 실패한 경우(원인: {@link javax.crypto.AEADBadTagException})
     */
    public static String decrypt(String ciphertext, SecretKey key) {
        if (ciphertext == null) {
            throw new IllegalArgumentException(NULL_CIPHERTEXT_MESSAGE);
        }
        if (key == null) {
            throw new IllegalArgumentException(NULL_KEY_MESSAGE);
        }

        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException e) {
            throw new CryptoException(MALFORMED_CIPHERTEXT_MESSAGE, e);
        }
        // 정상 암호문의 최소 길이는 IV(12) + GCM 태그(16) = 28바이트다(빈 평문의 암호문도 28바이트).
        // 28바이트 미만이면 구조적으로 손상된(태그를 담을 수 없는) 페이로드이므로 "인증 실패=변조"가
        // 아니라 포맷 오류(MALFORMED)로 분류한다. 그렇지 않으면 doFinal에서 AEADBadTagException으로
        // 던져져 변조와 혼동된다.
        if (combined.length < IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
            throw new CryptoException(MALFORMED_CIPHERTEXT_MESSAGE);
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            // IV는 combined의 앞 12바이트를 offset 참조해 중복 복사를 피한다.
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, combined, 0, IV_LENGTH_BYTES));
            // 암호문(+태그)도 offset 기반으로 직접 넘겨 전체 페이로드 복사를 1회 제거한다.
            byte[] decrypted = cipher.doFinal(combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException(변조/잘못된 키 등)을 포함한 모든 보안 예외를 원인 보존해 래핑한다.
            throw new CryptoException("Failed to decrypt ciphertext (authentication may have failed).", e);
        }
    }
}
