package ai.genesislab.crypto;

/**
 * 암호화 모듈의 운영 실패를 표현하는 비검사(unchecked) 예외.
 *
 * <p>JDK 암호화 API({@code javax.crypto})는 다양한 검사 예외
 * ({@link java.security.GeneralSecurityException} 계열)를 던지지만, 라이브러리 사용자에게
 * 검사 예외를 강제하지 않도록 본 예외로 일관되게 래핑한다. 단, <b>원인(cause)은 항상 보존</b>한다.</p>
 *
 * <p>대표적으로 GCM 인증 태그 검증 실패는 원인으로
 * {@link javax.crypto.AEADBadTagException}을 담는다. 호출자는 {@link #getCause()}로 실패 원인을
 * 구분할 수 있다.</p>
 */
public class CryptoException extends RuntimeException {

    /**
     * 메시지만으로 예외를 생성한다.
     *
     * @param message 사람이 읽을 수 있는 실패 설명
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * 메시지와 원인을 함께 담아 예외를 생성한다.
     *
     * @param message 사람이 읽을 수 있는 실패 설명
     * @param cause   근본 원인(예: {@link javax.crypto.AEADBadTagException})
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
