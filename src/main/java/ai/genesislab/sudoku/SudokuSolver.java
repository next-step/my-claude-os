package ai.genesislab.sudoku;

/**
 * 9x9 스도쿠 퍼즐을 백트래킹으로 푸는 풀이기(정적 메서드 모음).
 *
 * <p>{@link #solve(int[][])}는 입력 퍼즐의 빈칸({@link SudokuValidator#EMPTY})을 채운
 * <b>해 보드를 새 배열로</b> 반환하며 입력 배열은 변형하지 않는다(부수효과 없음).</p>
 *
 * <p>정통 스도쿠는 유일해를 가진다는 도메인 규칙을 강제한다. 즉 첫 해를 찾은 뒤에도 두 번째
 * 해를 탐색해, 해가 둘 이상이면 비유일로 보고 {@link SudokuException}을 던진다.</p>
 *
 * <p>입력 검증 순서는 <b>형식 → 모순 → 풀이</b>다. 형식 오류(null·크기·범위)와 이미 규칙을
 * 위반한 모순 보드는 {@link IllegalArgumentException}으로, 풀이 단계의 도메인 실패(해 없음·
 * 비유일)는 {@link SudokuException}으로 명확히 구분해 던진다.</p>
 *
 * <p>본 클래스는 상태가 없으므로 인스턴스화하지 않는다(정적 메서드 모음).</p>
 */
public final class SudokuSolver {

    /** 입력 보드가 이미 스도쿠 규칙을 위반(중복)한 모순 보드일 때의 예외 메시지. */
    public static final String CONTRADICTORY_BOARD_MESSAGE =
            "puzzle is contradictory: it already violates Sudoku rules.";

    /** 형식·모순은 정상이나 해가 존재하지 않을 때의 예외 메시지. */
    public static final String NO_SOLUTION_MESSAGE = "puzzle has no solution.";

    /** 해가 둘 이상이라 유일하지 않을 때의 예외 메시지. */
    public static final String MULTIPLE_SOLUTIONS_MESSAGE = "puzzle does not have a unique solution.";

    /**
     * 백트래킹 방문 노드 수가 방어적 상한({@link #MAX_SEARCH_NODES})을 초과했을 때의 예외 메시지.
     *
     * <p>형식·모순은 정상이지만 탐색 트리가 비정상적으로 폭발하는 입력에서 사실상 hang(스레드 자원 고갈)을
     * 막기 위한 안전장치다. 입력 <b>형식</b> 문제가 아니라 풀이 <b>자원 한계</b>이므로
     * {@link IllegalArgumentException}이 아닌 {@link SudokuException}으로 던지며,
     * {@link #NO_SOLUTION_MESSAGE}/{@link #MULTIPLE_SOLUTIONS_MESSAGE}와도 구분된다.</p>
     */
    public static final String SEARCH_LIMIT_EXCEEDED_MESSAGE =
            "puzzle search exceeded the node limit; aborting to avoid resource exhaustion.";

    /**
     * 백트래킹이 방문할 수 있는 최대 노드(셀 배치 시도) 수의 방어적 상한.
     *
     * <p>MRV(최소 후보 셀 우선) 최적화를 적용한 정상 9x9 유일해 퍼즐은 이 값에 한참 못 미치는
     * 노드만 방문한다(보통 수천 노드 이내). 따라서 정상 입력은 절대 가드에 걸리지 않으며,
     * 탐색 트리가 비정상적으로 폭발하는 경우에만 {@link SudokuException}으로 중단한다.</p>
     */
    public static final int MAX_SEARCH_NODES = 2_000_000;

    private SudokuSolver() {
        // 유틸리티 클래스: 인스턴스화 방지.
        throw new AssertionError("No ai.genesislab.sudoku.SudokuSolver instances for you!");
    }

    /**
     * 스도쿠 퍼즐을 풀어 빈칸이 모두 채워진 유일한 해 보드를 새 배열로 반환한다.
     *
     * @param puzzle 풀이할 9x9 퍼즐(빈칸은 0). 변형하지 않는다.
     * @return 빈칸이 모두 채워진 해 보드(입력과 독립적인 새 {@code int[9][9]})
     * @throws IllegalArgumentException 형식이 잘못됐거나({@link SudokuValidator#validateFormat(int[][])})
     *                                  이미 규칙을 위반한 모순 보드인 경우
     * @throws SudokuException          해가 없거나(해 없음), 해가 둘 이상이거나(비유일),
     *                                  탐색 노드 수가 {@link #MAX_SEARCH_NODES}를 초과한 경우
     */
    public static int[][] solve(int[][] puzzle) {
        // 1) 형식 검증(null·크기·범위) + 모순 검사를 한 번에 수행한다.
        //    isValid가 내부에서 validateFormat을 먼저 호출하므로(형식 오류 → IllegalArgumentException,
        //    그 메시지는 NULL_BOARD/INVALID_SIZE/VALUE_OUT_OF_RANGE 등으로 그대로 전파),
        //    별도의 validateFormat 직접 호출을 제거해 O(81) 형식 패스를 이중으로 돌지 않는다.
        //    형식이 통과한 뒤 중복(모순)이면 false가 돌아오므로 CONTRADICTORY로 매핑한다.
        //    → 형식 오류(IllegalArgument)가 먼저, 그 다음 모순(IllegalArgument)이라는 우선순위가 보존된다.
        if (!SudokuValidator.isValid(puzzle)) {
            throw new IllegalArgumentException(CONTRADICTORY_BOARD_MESSAGE);
        }

        // 2) 입력을 복사해(원본 불변) 백트래킹으로 푼다.
        int[][] working = copy(puzzle);
        int[][] solution = new int[SudokuValidator.SIZE][SudokuValidator.SIZE];
        long[] nodeBudget = {MAX_SEARCH_NODES};
        int solutionCount = countSolutions(working, solution, 0, nodeBudget);

        if (solutionCount == 0) {
            throw new SudokuException(NO_SOLUTION_MESSAGE);
        }
        if (solutionCount > 1) {
            throw new SudokuException(MULTIPLE_SOLUTIONS_MESSAGE);
        }
        return solution;
    }

    /**
     * 백트래킹으로 해를 탐색하되, 유일성 판정을 위해 최대 2개까지만 센다.
     *
     * <p><b>MRV(Minimum Remaining Values) 최적화</b>: 다음에 채울 빈칸을 단순히 "첫 빈칸"이 아니라
     * "합법 후보 수가 가장 적은 빈칸"으로 고른다. 후보가 적은 칸부터 분기하면 탐색 트리의 분기 폭이
     * 크게 줄어, 단서가 희박한 유효 퍼즐에서도 트리 폭발 없이 빠르게 푼다. 후보가 0개인 칸을 만나면
     * 즉시 막다른 길로 처리한다. 이 최적화는 <b>탐색 순서</b>만 바꿀 뿐, 첫 해를 찾은 뒤에도 두 번째
     * 해까지 세는 <b>유일해 강제 의미는 그대로</b>다.</p>
     *
     * <p><b>방어적 가드</b>: 방문 노드(셀 배치 시도) 수를 {@code nodeBudget}으로 차감하며, 0 이하로
     * 떨어지면 {@link SudokuException}({@link #SEARCH_LIMIT_EXCEEDED_MESSAGE})을 던져 비정상적인
     * 탐색 폭발로 인한 자원 고갈을 막는다. MRV 적용 후 정상 유일해 퍼즐은 이 상한에 한참 못 미친다.</p>
     *
     * <p>첫 해를 발견하면 {@code solution}에 깊은 복사로 저장하고, 두 번째 해가 발견되면
     * 즉시 탐색을 멈춘다(2 이상이면 비유일로 충분하므로 전수 탐색하지 않는다).</p>
     *
     * @param board      현재 진행 중인 작업 보드(빈칸을 채워가며 변형됨)
     * @param solution   첫 해를 저장할 출력 보드
     * @param found      지금까지 발견한 해의 개수
     * @param nodeBudget 길이 1의 가변 카운터(남은 노드 예산). 각 후보 시도마다 1 차감한다.
     * @return 누적 해 개수(0, 1, 또는 2). 2는 "2개 이상"을 의미한다.
     * @throws SudokuException 방문 노드 수가 {@link #MAX_SEARCH_NODES}를 초과한 경우
     */
    private static int countSolutions(int[][] board, int[][] solution, int found, long[] nodeBudget) {
        // MRV: 후보 수가 가장 적은 빈칸을 찾는다.
        int bestRow = -1;
        int bestCol = -1;
        int bestCandidateCount = SudokuValidator.MAX_VALUE + 1;
        int size = SudokuValidator.SIZE;
        for (int r = 0; r < size && bestCandidateCount > 1; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] != SudokuValidator.EMPTY) {
                    continue;
                }
                int candidates = 0;
                for (int value = SudokuValidator.MIN_VALUE; value <= SudokuValidator.MAX_VALUE; value++) {
                    if (canPlace(board, r, c, value)) {
                        candidates++;
                    }
                }
                if (candidates < bestCandidateCount) {
                    bestCandidateCount = candidates;
                    bestRow = r;
                    bestCol = c;
                    if (candidates <= 1) {
                        break; // 후보 0~1개면 더 좋은 칸이 없으니 조기 중단.
                    }
                }
            }
        }

        // 빈칸이 하나도 없으면 완성된 해를 하나 찾은 것.
        if (bestRow == -1) {
            if (found == 0) {
                deepCopyInto(board, solution); // 첫 해만 보존.
            }
            return found + 1;
        }

        // 선택된 빈칸에 후보가 없으면 막다른 길(해 없음).
        for (int value = SudokuValidator.MIN_VALUE; value <= SudokuValidator.MAX_VALUE; value++) {
            if (canPlace(board, bestRow, bestCol, value)) {
                if (--nodeBudget[0] < 0) {
                    // 방어적 가드: 비정상 탐색 폭발을 자원 한계로 중단(형식 문제가 아님).
                    throw new SudokuException(SEARCH_LIMIT_EXCEEDED_MESSAGE);
                }
                board[bestRow][bestCol] = value;
                found = countSolutions(board, solution, found, nodeBudget);
                board[bestRow][bestCol] = SudokuValidator.EMPTY; // 백트래킹: 원복
                if (found >= 2) {
                    return found; // 비유일 확정 — 조기 종료
                }
            }
        }
        return found;
    }

    /**
     * {@code (row, col)} 칸에 {@code value}를 두어도 행·열·3x3 박스 규칙을 위반하지 않는지 검사한다.
     *
     * @param board 현재 작업 보드
     * @param row   행 인덱스
     * @param col   열 인덱스
     * @param value 시도할 값(1~9)
     * @return 위반이 없으면 {@code true}
     */
    private static boolean canPlace(int[][] board, int row, int col, int value) {
        int size = SudokuValidator.SIZE;
        int boxSize = SudokuValidator.BOX_SIZE;
        for (int i = 0; i < size; i++) {
            if (board[row][i] == value || board[i][col] == value) {
                return false;
            }
        }
        int boxRow = (row / boxSize) * boxSize;
        int boxCol = (col / boxSize) * boxSize;
        for (int r = boxRow; r < boxRow + boxSize; r++) {
            for (int c = boxCol; c < boxCol + boxSize; c++) {
                if (board[r][c] == value) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 입력 보드의 깊은 복사본을 만든다(원본 불변 보장).
     *
     * @param board 원본 9x9 보드
     * @return 독립적인 새 {@code int[9][9]} 복사본
     */
    private static int[][] copy(int[][] board) {
        int size = SudokuValidator.SIZE;
        int[][] result = new int[size][];
        for (int r = 0; r < size; r++) {
            result[r] = board[r].clone();
        }
        return result;
    }

    /**
     * {@code source}의 내용을 {@code target}에 깊은 복사한다.
     *
     * @param source 원본 보드
     * @param target 복사 대상 보드
     */
    private static void deepCopyInto(int[][] source, int[][] target) {
        for (int r = 0; r < SudokuValidator.SIZE; r++) {
            System.arraycopy(source[r], 0, target[r], 0, SudokuValidator.SIZE);
        }
    }
}
