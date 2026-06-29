package ai.genesislab.sudoku;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.genesislab.testutil.UtilityClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SudokuValidator} 단위 테스트.
 *
 * <p>isValid(부분 보드 허용)·isComplete(빈칸 0개 + 규칙 준수)의 정상·경계·예외 케이스를 검증한다.</p>
 */
@DisplayName("SudokuValidator 단위 테스트")
class SudokuValidatorTest {

    /** 빈칸 없이 규칙을 만족하는 완성 보드(유효한 완전 해). */
    private static int[][] completeValidBoard() {
        return new int[][] {
                {5, 3, 4, 6, 7, 8, 9, 1, 2},
                {6, 7, 2, 1, 9, 5, 3, 4, 8},
                {1, 9, 8, 3, 4, 2, 5, 6, 7},
                {8, 5, 9, 7, 6, 1, 4, 2, 3},
                {4, 2, 6, 8, 5, 3, 7, 9, 1},
                {7, 1, 3, 9, 2, 4, 8, 5, 6},
                {9, 6, 1, 5, 3, 7, 2, 8, 4},
                {2, 8, 7, 4, 1, 9, 6, 3, 5},
                {3, 4, 5, 2, 8, 6, 1, 7, 9},
        };
    }

    /** 전부 빈칸인 보드(부분 보드). */
    private static int[][] emptyBoard() {
        return new int[SudokuValidator.SIZE][SudokuValidator.SIZE];
    }

    @Nested
    @DisplayName("isValid(board) — 규칙 위반 검사(부분 보드 허용)")
    class IsValid {

        @Test
        @DisplayName("정상: 완성된 유효 보드는 true")
        void isValid_completeValidBoard_returnsTrue() {
            assertTrue(SudokuValidator.isValid(completeValidBoard()));
        }

        @Test
        @DisplayName("경계: 전부 빈칸(0)인 보드도 규칙 위반이 없으므로 true")
        void isValid_emptyBoard_returnsTrue() {
            assertTrue(SudokuValidator.isValid(emptyBoard()));
        }

        @Test
        @DisplayName("경계: 일부만 채워진 부분 보드도 중복 없으면 true")
        void isValid_partialBoardWithoutDuplicates_returnsTrue() {
            int[][] board = emptyBoard();
            board[0][0] = 5;
            board[4][4] = 5; // 다른 행·열·박스라 중복 아님
            board[8][8] = 9;
            assertTrue(SudokuValidator.isValid(board));
        }

        @Test
        @DisplayName("예외(규칙): 같은 행에 중복이 있으면 false")
        void isValid_duplicateInRow_returnsFalse() {
            int[][] board = emptyBoard();
            board[0][0] = 3;
            board[0][5] = 3; // 같은 행 중복
            assertFalse(SudokuValidator.isValid(board));
        }

        @Test
        @DisplayName("예외(규칙): 같은 열에 중복이 있으면 false")
        void isValid_duplicateInColumn_returnsFalse() {
            int[][] board = emptyBoard();
            board[0][2] = 7;
            board[6][2] = 7; // 같은 열 중복
            assertFalse(SudokuValidator.isValid(board));
        }

        @Test
        @DisplayName("경계(박스): 같은 3x3 박스 안 중복이면 false")
        void isValid_duplicateInBox_returnsFalse() {
            int[][] board = emptyBoard();
            board[0][0] = 4;
            board[2][2] = 4; // 같은 좌상단 박스, 다른 행·열
            assertFalse(SudokuValidator.isValid(board));
        }

        @Test
        @DisplayName("경계(박스 경계): 박스 경계를 넘나드는 같은 행 값은 중복이 아니다")
        void isValid_acrossBoxBoundarySameRow_isNotBoxDuplicate() {
            int[][] board = emptyBoard();
            board[0][2] = 6; // 좌상단 박스
            board[1][3] = 6; // 가운데상단 박스 — 다른 행·열·박스라 중복 아님
            assertTrue(SudokuValidator.isValid(board));
        }
    }

    @Nested
    @DisplayName("isComplete(board) — 완성 보드 검사")
    class IsComplete {

        @Test
        @DisplayName("정상: 빈칸 없는 유효 보드는 true")
        void isComplete_completeValidBoard_returnsTrue() {
            assertTrue(SudokuValidator.isComplete(completeValidBoard()));
        }

        @Test
        @DisplayName("경계: 단 한 칸만 비어 있어도 false")
        void isComplete_oneCellEmpty_returnsFalse() {
            int[][] board = completeValidBoard();
            board[4][4] = SudokuValidator.EMPTY;
            assertFalse(SudokuValidator.isComplete(board));
        }

        @Test
        @DisplayName("경계: 전부 빈칸이면 false")
        void isComplete_emptyBoard_returnsFalse() {
            assertFalse(SudokuValidator.isComplete(emptyBoard()));
        }

        @Test
        @DisplayName("예외(규칙): 가득 찼지만 규칙을 위반하면 false")
        void isComplete_filledButInvalid_returnsFalse() {
            int[][] board = completeValidBoard();
            // 한 칸을 바꿔 같은 행에 중복을 만든다(빈칸은 없지만 규칙 위반).
            board[0][0] = board[0][1]; // 3 == 3 중복
            assertFalse(SudokuValidator.isComplete(board));
        }
    }

    @Nested
    @DisplayName("형식 검증 예외 — IllegalArgumentException(공개 상수 메시지)")
    class FormatValidation {

        @Test
        @DisplayName("예외: null 보드는 IllegalArgumentException")
        void isValid_nullBoard_throws() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SudokuValidator.isValid(null));
            assertEquals(SudokuValidator.NULL_BOARD_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: null 행이 있으면 IllegalArgumentException")
        void isValid_nullRow_throws() {
            int[][] board = new int[SudokuValidator.SIZE][];
            for (int r = 0; r < SudokuValidator.SIZE; r++) {
                board[r] = new int[SudokuValidator.SIZE];
            }
            board[3] = null;
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SudokuValidator.isValid(board));
            assertEquals(SudokuValidator.NULL_ROW_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: 행 개수가 9가 아니면 IllegalArgumentException")
        void isValid_wrongRowCount_throws() {
            int[][] board = new int[8][SudokuValidator.SIZE]; // 8행
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SudokuValidator.isValid(board));
            assertEquals(SudokuValidator.INVALID_SIZE_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: 열 개수가 9가 아니면 IllegalArgumentException")
        void isComplete_wrongColumnCount_throws() {
            int[][] board = new int[SudokuValidator.SIZE][SudokuValidator.SIZE];
            board[2] = new int[10]; // 10열
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SudokuValidator.isComplete(board));
            assertEquals(SudokuValidator.INVALID_SIZE_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: 값이 9를 초과하면 IllegalArgumentException")
        void isValid_valueAboveRange_throws() {
            int[][] board = emptyBoard();
            board[0][0] = 10;
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SudokuValidator.isValid(board));
            assertEquals(SudokuValidator.VALUE_OUT_OF_RANGE_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: 값이 음수면 IllegalArgumentException")
        void isValid_negativeValue_throws() {
            int[][] board = emptyBoard();
            board[8][8] = -1;
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SudokuValidator.isValid(board));
            assertEquals(SudokuValidator.VALUE_OUT_OF_RANGE_MESSAGE, ex.getMessage());
        }
    }

    @Nested
    @DisplayName("부수효과 없음 — 입력 보드 불변")
    class NoSideEffects {

        @Test
        @DisplayName("isValid는 입력 보드를 변형하지 않는다")
        void isValid_doesNotMutateInput() {
            int[][] board = completeValidBoard();
            int[][] snapshot = deepCopy(board);
            SudokuValidator.isValid(board);
            for (int r = 0; r < SudokuValidator.SIZE; r++) {
                assertArrayEquals(snapshot[r], board[r]);
            }
        }

        @Test
        @DisplayName("isComplete는 입력 보드를 변형하지 않는다")
        void isComplete_doesNotMutateInput() {
            int[][] board = completeValidBoard();
            int[][] snapshot = deepCopy(board);
            SudokuValidator.isComplete(board);
            for (int r = 0; r < SudokuValidator.SIZE; r++) {
                assertArrayEquals(snapshot[r], board[r]);
            }
        }

        private static int[][] deepCopy(int[][] board) {
            int[][] copy = new int[board.length][];
            for (int r = 0; r < board.length; r++) {
                copy[r] = board[r].clone();
            }
            return copy;
        }
    }

    @Test
    @DisplayName("설계: 유틸리티 클래스는 인스턴스화할 수 없다")
    void sudokuValidator_cannotBeInstantiated() throws Exception {
        UtilityClasses.assertNotInstantiable(SudokuValidator.class);
    }
}
