package ai.genesislab.sudoku;

/**
 * 9x9 스도쿠 보드의 규칙 위반 여부를 검사하는 검증기(정적 메서드 모음).
 *
 * <p>보드는 {@code int[9][9]}로 표현하며, 각 칸은 빈칸을 의미하는 {@code 0}({@link #EMPTY})
 * 또는 1~9의 숫자다. {@link #isValid(int[][])}는 채워진 칸만 검사하므로 부분(미완성) 보드도
 * 검증할 수 있고, {@link #isComplete(int[][])}는 빈칸이 하나도 없으면서 규칙을 만족하는
 * 완성 보드인지 검사한다.</p>
 *
 * <p>형식 검증(null·크기·값 범위)은 {@link #validateFormat(int[][])}로 공통화해
 * {@link SudokuSolver}가 재사용한다. 모든 메서드는 입력 배열을 변형하지 않는다.</p>
 *
 * <p>본 클래스는 상태가 없으므로 인스턴스화하지 않는다(정적 메서드 모음).</p>
 */
public final class SudokuValidator {

    /** 보드 한 변의 길이(9). */
    public static final int SIZE = 9;

    /** 3x3 박스 한 변의 길이(3). */
    public static final int BOX_SIZE = 3;

    /** 빈칸을 나타내는 값(0). */
    public static final int EMPTY = 0;

    /** 셀이 가질 수 있는 최소 숫자(1). */
    public static final int MIN_VALUE = 1;

    /** 셀이 가질 수 있는 최대 숫자(9). */
    public static final int MAX_VALUE = 9;

    /** 보드가 null일 때의 예외 메시지. */
    public static final String NULL_BOARD_MESSAGE = "board must not be null.";

    /** 보드의 행(row)이 null일 때의 예외 메시지. */
    public static final String NULL_ROW_MESSAGE = "board rows must not be null.";

    /** 보드 크기가 9x9가 아닐 때의 예외 메시지. */
    public static final String INVALID_SIZE_MESSAGE = "board must be a 9x9 grid.";

    /** 셀 값이 0~9 범위를 벗어났을 때의 예외 메시지. */
    public static final String VALUE_OUT_OF_RANGE_MESSAGE = "cell values must be between 0 and 9 (0 means empty).";

    private SudokuValidator() {
        // 유틸리티 클래스: 인스턴스화 방지.
        throw new AssertionError("No ai.genesislab.sudoku.SudokuValidator instances for you!");
    }

    /**
     * 보드의 형식을 검증한다(null·크기·값 범위). 규칙 위반(중복) 여부는 검사하지 않는다.
     *
     * <p>형식 검증은 {@link #isValid(int[][])}/{@link #isComplete(int[][])}/
     * {@link SudokuSolver#solve(int[][])}가 공유하므로 여기로 공통화했다.</p>
     *
     * @param board 검사할 9x9 보드
     * @throws IllegalArgumentException 보드 또는 행이 null이거나, 9x9가 아니거나, 셀 값이 0~9 밖인 경우
     */
    public static void validateFormat(int[][] board) {
        if (board == null) {
            throw new IllegalArgumentException(NULL_BOARD_MESSAGE);
        }
        if (board.length != SIZE) {
            throw new IllegalArgumentException(INVALID_SIZE_MESSAGE);
        }
        for (int[] row : board) {
            if (row == null) {
                throw new IllegalArgumentException(NULL_ROW_MESSAGE);
            }
            if (row.length != SIZE) {
                throw new IllegalArgumentException(INVALID_SIZE_MESSAGE);
            }
            for (int value : row) {
                if (value < EMPTY || value > MAX_VALUE) {
                    throw new IllegalArgumentException(VALUE_OUT_OF_RANGE_MESSAGE);
                }
            }
        }
    }

    /**
     * 보드가 스도쿠 규칙을 위반하지 않는지 검사한다(부분 보드 허용).
     *
     * <p>빈칸({@link #EMPTY})은 무시하고, 채워진 칸(1~9)들이 같은 행·열·3x3 박스 안에서
     * 중복되지 않으면 {@code true}를 반환한다. 따라서 아직 다 채우지 않은 보드도 검사할 수 있다.</p>
     *
     * @param board 검사할 9x9 보드(변형하지 않음)
     * @return 채워진 칸들이 규칙을 위반하지 않으면 {@code true}
     * @throws IllegalArgumentException 보드 형식이 잘못된 경우({@link #validateFormat(int[][])} 참고)
     */
    public static boolean isValid(int[][] board) {
        validateFormat(board);
        return hasNoDuplicates(board);
    }

    /**
     * 보드가 빈칸 없이 완전히 채워졌고 규칙도 위반하지 않는 완성 보드인지 검사한다.
     *
     * @param board 검사할 9x9 보드(변형하지 않음)
     * @return 빈칸이 하나도 없고 {@link #isValid(int[][])}가 참이면 {@code true}
     * @throws IllegalArgumentException 보드 형식이 잘못된 경우({@link #validateFormat(int[][])} 참고)
     */
    public static boolean isComplete(int[][] board) {
        validateFormat(board);
        for (int[] row : board) {
            for (int value : row) {
                if (value == EMPTY) {
                    return false;
                }
            }
        }
        return hasNoDuplicates(board);
    }

    /**
     * 형식이 이미 검증된 보드에서 채워진 칸들의 행·열·박스 중복 여부를 검사한다.
     *
     * @param board 형식이 검증된 9x9 보드
     * @return 중복이 없으면 {@code true}
     */
    private static boolean hasNoDuplicates(int[][] board) {
        // 행·열·박스별로 1~9 등장 여부를 비트마스크로 추적한다(인덱스 0은 미사용).
        boolean[][] rowSeen = new boolean[SIZE][MAX_VALUE + 1];
        boolean[][] colSeen = new boolean[SIZE][MAX_VALUE + 1];
        boolean[][] boxSeen = new boolean[SIZE][MAX_VALUE + 1];

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int value = board[r][c];
                if (value == EMPTY) {
                    continue;
                }
                int box = (r / BOX_SIZE) * BOX_SIZE + (c / BOX_SIZE);
                if (rowSeen[r][value] || colSeen[c][value] || boxSeen[box][value]) {
                    return false;
                }
                rowSeen[r][value] = true;
                colSeen[c][value] = true;
                boxSeen[box][value] = true;
            }
        }
        return true;
    }
}
