package ai.genesislab.sudoku;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스도쿠 모듈 통합 테스트.
 *
 * <p>{@link SudokuSolver}와 {@link SudokuValidator}의 연동 흐름을 검증한다. 핵심은
 * "퍼즐 → solve → 결과를 다시 isValid/isComplete로 재검증"하는 라운드트립이다. 단위 테스트가
 * 각 메서드의 개별 계약을 본다면, 여기서는 두 컴포넌트가 한 흐름에서 일관되게 동작하는지를 본다.</p>
 */
@DisplayName("Sudoku 모듈 통합 테스트")
class SudokuIntegrationTest {

    @Test
    @DisplayName("라운드트립: 유일해 퍼즐을 solve한 결과는 isComplete==true, isValid==true")
    void solve_thenValidate_resultIsCompleteAndValid() {
        int[][] puzzle = SudokuSolverTest.uniquePuzzle();

        int[][] solution = SudokuSolver.solve(puzzle);

        // solve의 출력이 Validator의 두 계약을 모두 만족한다(컴포넌트 연동의 핵심 가치).
        assertTrue(SudokuValidator.isValid(solution), "해는 규칙을 위반하지 않아야 한다");
        assertTrue(SudokuValidator.isComplete(solution), "해는 빈칸 없이 완성되어야 한다");
    }

    @Test
    @DisplayName("라운드트립: 원본 퍼즐은 부분 보드로서 isValid==true지만 isComplete==false")
    void originalPuzzle_isPartiallyValidButNotComplete() {
        int[][] puzzle = SudokuSolverTest.uniquePuzzle();

        // 풀기 전 퍼즐도 규칙 위반은 없어야 하고(부분 보드), 빈칸이 있으니 완성은 아니다.
        assertTrue(SudokuValidator.isValid(puzzle));
        assertFalse(SudokuValidator.isComplete(puzzle));

        SudokuSolver.solve(puzzle); // 풀이가 원본을 건드리지 않는지 함께 확인
        assertFalse(SudokuValidator.isComplete(puzzle), "solve는 원본을 변형하지 않는다");
    }

    @Test
    @DisplayName("라운드트립: solve 결과는 원본 단서를 모두 보존한다(주어진 칸 불변)")
    void solution_preservesOriginalClues() {
        int[][] puzzle = SudokuSolverTest.uniquePuzzle();

        int[][] solution = SudokuSolver.solve(puzzle);

        // 원래 채워져 있던 단서는 해에서도 같은 값이어야 한다.
        for (int r = 0; r < SudokuValidator.SIZE; r++) {
            for (int c = 0; c < SudokuValidator.SIZE; c++) {
                if (puzzle[r][c] != SudokuValidator.EMPTY) {
                    assertEquals(puzzle[r][c], solution[r][c],
                            "단서 (" + r + "," + c + ")는 해에서도 보존되어야 한다");
                }
            }
        }
        assertArrayEquals(SudokuSolverTest.uniqueSolution(), solution);
    }

    @Test
    @DisplayName("라운드트립: 완성 해의 한 칸을 비우면 다시 같은 유일해로 복원된다")
    void removeOneCell_thenSolve_restoresSameSolution() {
        int[][] solution = SudokuSolverTest.uniqueSolution();

        // 완성 해에서 칸 하나를 비워 퍼즐을 만들고 다시 풀면 동일 해가 나와야 한다(유일성 일관성).
        int[][] puzzle = SudokuSolverTest.uniqueSolution();
        puzzle[5][5] = SudokuValidator.EMPTY;

        int[][] resolved = SudokuSolver.solve(puzzle);
        assertArrayEquals(solution, resolved);
        assertTrue(SudokuValidator.isComplete(resolved));
    }

    @Test
    @DisplayName("에러 계약 일관성: 형식·모순은 IllegalArgumentException, 풀이 실패는 SudokuException")
    void errorContract_distinguishesIllegalArgumentFromSudokuException() {
        // 형식 오류(범위 밖) → IllegalArgumentException + VALUE_OUT_OF_RANGE_MESSAGE
        int[][] badFormat = SudokuSolverTest.uniquePuzzle();
        badFormat[0][2] = 42;
        IllegalArgumentException formatEx = assertThrows(IllegalArgumentException.class,
                () -> SudokuSolver.solve(badFormat));
        assertEquals(SudokuValidator.VALUE_OUT_OF_RANGE_MESSAGE, formatEx.getMessage());

        // 모순(중복) → IllegalArgumentException + CONTRADICTORY_BOARD_MESSAGE
        int[][] contradictory = SudokuSolverTest.uniquePuzzle();
        contradictory[0][2] = 5; // (0,0)==5 와 같은 행 중복
        IllegalArgumentException contradictoryEx = assertThrows(IllegalArgumentException.class,
                () -> SudokuSolver.solve(contradictory));
        assertEquals(SudokuSolver.CONTRADICTORY_BOARD_MESSAGE, contradictoryEx.getMessage());

        // 풀이 도메인 실패(비유일) → SudokuException + MULTIPLE_SOLUTIONS_MESSAGE
        int[][] empty = new int[SudokuValidator.SIZE][SudokuValidator.SIZE];
        SudokuException multipleEx = assertThrows(SudokuException.class,
                () -> SudokuSolver.solve(empty));
        assertEquals(SudokuSolver.MULTIPLE_SOLUTIONS_MESSAGE, multipleEx.getMessage());
    }
}
