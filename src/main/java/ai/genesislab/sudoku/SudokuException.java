package ai.genesislab.sudoku;

/**
 * 스도쿠 모듈의 운영/도메인 실패를 표현하는 비검사(unchecked) 예외.
 *
 * <p>입력의 <b>형식·계약 위반</b>(null 보드/행, 9x9 아닌 크기, 값이 0~9 밖, 이미 규칙을
 * 위반한 모순 보드)은 표준 {@link IllegalArgumentException}으로 거부한다. 반면 형식은
 * 올바르지만 <b>풀이 단계에서 발생하는 도메인 실패</b>(해가 존재하지 않음, 해가 둘 이상이라
 * 유일하지 않음)는 본 예외로 일관되게 표현한다.</p>
 *
 * <p>{@code ai.genesislab.crypto.CryptoException}과 동일한 구조(생성자 2종)를 따른다.
 * JDK 검사 예외를 래핑할 일이 생기면 원인(cause)을 항상 보존한다.</p>
 */
public class SudokuException extends RuntimeException {

    /**
     * 메시지만으로 예외를 생성한다.
     *
     * @param message 사람이 읽을 수 있는 실패 설명
     */
    public SudokuException(String message) {
        super(message);
    }

    /**
     * 메시지와 원인을 함께 담아 예외를 생성한다.
     *
     * @param message 사람이 읽을 수 있는 실패 설명
     * @param cause   근본 원인
     */
    public SudokuException(String message, Throwable cause) {
        super(message, cause);
    }
}
