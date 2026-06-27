package ai.genesislab.crypto;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.nio.charset.StandardCharsets;

/**
 * BCrypt 기반 단방향(비가역) 비밀번호 해싱 유틸리티.
 *
 * <p>외부 라이브러리 {@code at.favre.lib:bcrypt}를 내부 구현 의존성으로만 사용하며, BCrypt 타입은
 * public 시그니처에 노출하지 않는다(입출력은 모두 {@code String}/{@code boolean}).</p>
 *
 * <p>해시 문자열에는 salt와 cost factor가 내장된다. 따라서 {@link #matches(String, String)}는
 * 별도의 salt 전달 없이 저장된 해시만으로 검증할 수 있다.</p>
 *
 * <p><b>BCrypt 72바이트 한계:</b> BCrypt 알고리즘은 입력 비밀번호를 최대 72바이트까지만 사용한다.
 * 본 구현은 라이브러리 기본(strict) 전략을 따르므로 한계를 초과하는 입력은
 * {@link IllegalArgumentException}으로 거부된다(조용한 절단으로 인한 보안 사고를 방지).</p>
 *
 * <p>본 클래스는 상태가 없으므로 인스턴스화하지 않는다(정적 메서드 모음).</p>
 */
public final class PasswordHasher {

    /** 기본 cost factor. 허용 범위(10~12) 내이며, 업계 기본값이자 테스트 속도를 고려한 값이다. */
    public static final int COST_FACTOR = 10;

    /** BCrypt가 실제로 사용하는 비밀번호 최대 길이(UTF-8 인코딩 바이트 기준, 문자 길이 아님). */
    public static final int MAX_PASSWORD_BYTES = 72;

    /** 평문 비밀번호가 null일 때의 예외 메시지. */
    public static final String NULL_RAW_PASSWORD_MESSAGE = "rawPassword must not be null.";

    /** 평문 비밀번호가 빈 문자열일 때의 예외 메시지. */
    public static final String EMPTY_RAW_PASSWORD_MESSAGE = "rawPassword must not be empty.";

    /** 평문 비밀번호가 BCrypt 72바이트 한계를 초과할 때의 예외 메시지(라이브러리 영문 메시지에 의존하지 않음). */
    public static final String PASSWORD_TOO_LONG_MESSAGE =
            "rawPassword must not exceed " + MAX_PASSWORD_BYTES + " bytes when UTF-8 encoded.";

    /** 해시가 null일 때의 예외 메시지. */
    public static final String NULL_HASH_MESSAGE = "hash must not be null.";

    private PasswordHasher() {
        // 유틸리티 클래스: 인스턴스화 방지.
        throw new AssertionError("No ai.genesislab.crypto.PasswordHasher instances for you!");
    }

    /**
     * 평문 비밀번호를 BCrypt로 해싱한다. salt는 매 호출마다 무작위로 생성되어 결과에 내장된다.
     *
     * @param rawPassword 평문 비밀번호(null·빈 문자열 불가)
     * @return salt와 cost가 내장된 BCrypt 해시 문자열(예: {@code $2a$10$...})
     * @throws IllegalArgumentException {@code rawPassword}가 null이거나 비었거나, BCrypt 72바이트
     *                                  한계를 초과하는 경우
     */
    public static String hash(String rawPassword) {
        validateRawPassword(rawPassword);
        return BCrypt.withDefaults().hashToString(COST_FACTOR, rawPassword.toCharArray());
    }

    /**
     * 평문 비밀번호가 저장된 해시와 일치하는지 검증한다.
     *
     * <p>입력 검증은 {@link #hash(String)}와 <b>대칭</b>이다: {@code rawPassword}의 null/빈 문자열/72바이트
     * 초과는 {@code hash()}와 동일하게 {@link IllegalArgumentException}으로 거부한다(조용한 {@code false} 금지).
     * {@code false}는 <b>오직 해시 문자열 포맷이 잘못된 경우</b>(파싱 불가 등)로 한정한다.</p>
     *
     * @param rawPassword 평문 비밀번호(null·빈 문자열 불가, UTF-8 72바이트 이하)
     * @param hash        검증 대상 해시 문자열(null 불가)
     * @return 일치하면 {@code true}, 불일치하거나 해시 포맷이 잘못됐으면 {@code false}
     * @throws IllegalArgumentException {@code rawPassword}가 null/빈 문자열/72바이트 초과이거나 {@code hash}가 null인 경우
     */
    public static boolean matches(String rawPassword, String hash) {
        // 입력 검증을 try 바깥에서 먼저 수행해 라이브러리(verify) 예외와 명확히 구분한다.
        validateRawPassword(rawPassword);
        if (hash == null) {
            throw new IllegalArgumentException(NULL_HASH_MESSAGE);
        }
        try {
            return BCrypt.verifyer().verify(rawPassword.toCharArray(), hash.toCharArray()).verified;
        } catch (IllegalArgumentException e) {
            // 여기 도달하는 IllegalArgumentException은 "잘못된 해시 포맷"뿐이다(rawPassword는 위에서 이미 검증됨).
            // 깨진 해시는 검증 불가 입력이므로 "불일치(false)"로 정의한다.
            return false;
        }
    }

    private static void validateRawPassword(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException(NULL_RAW_PASSWORD_MESSAGE);
        }
        if (rawPassword.isEmpty()) {
            throw new IllegalArgumentException(EMPTY_RAW_PASSWORD_MESSAGE);
        }
        // BCrypt는 입력을 최대 72바이트(UTF-8)까지만 사용한다. 라이브러리 영문 메시지에 의존하지 않고
        // hash()/matches() 양쪽에서 직접 길이를 검증해 조용한 절단을 막고 계약을 대칭으로 만든다.
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(PASSWORD_TOO_LONG_MESSAGE);
        }
    }
}
