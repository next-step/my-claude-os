package ai.genesislab.calculator;

/**
 * 부수효과 없는 순수 함수형 계산기 라이브러리.
 *
 * <p>모든 연산은 {@code double} 입력에 대해 결정적 출력을 반환하며, 외부 I/O나 가변 상태를
 * 두지 않는다. 0으로 나누는 경우({@link #divide}, {@link #modulo})에는 {@code double}의
 * 기본 동작(Infinity/NaN)에 의존하지 않고 명시적으로 {@link ArithmeticException}을 던진다.</p>
 *
 * <p>본 클래스는 상태가 없으므로 인스턴스화하지 않는다(정적 메서드 모음).</p>
 */
public final class Calculator {

    /** 0으로 나눌 때 사용하는 예외 메시지. */
    public static final String DIVIDE_BY_ZERO_MESSAGE = "Division by zero is not allowed.";

    /** 0으로 나머지를 구할 때 사용하는 예외 메시지. */
    public static final String MODULO_BY_ZERO_MESSAGE = "Modulo by zero is not allowed.";

    private Calculator() {
        // 유틸리티 클래스: 인스턴스화 방지.
        throw new AssertionError("No ai.genesislab.calculator.Calculator instances for you!");
    }

    /**
     * 두 수를 더한다.
     *
     * @param a 피연산자
     * @param b 피연산자
     * @return {@code a + b}
     */
    public static double add(double a, double b) {
        return a + b;
    }

    /**
     * 첫 번째 수에서 두 번째 수를 뺀다.
     *
     * @param a 피감수
     * @param b 감수
     * @return {@code a - b}
     */
    public static double subtract(double a, double b) {
        return a - b;
    }

    /**
     * 두 수를 곱한다.
     *
     * @param a 피연산자
     * @param b 피연산자
     * @return {@code a * b}
     */
    public static double multiply(double a, double b) {
        return a * b;
    }

    /**
     * 첫 번째 수를 두 번째 수로 나눈다.
     *
     * @param a 피제수
     * @param b 제수
     * @return {@code a / b}
     * @throws ArithmeticException {@code b}가 0인 경우
     */
    public static double divide(double a, double b) {
        if (b == 0.0) {
            throw new ArithmeticException(DIVIDE_BY_ZERO_MESSAGE);
        }
        return a / b;
    }

    /**
     * 밑을 지수만큼 거듭제곱한다.
     *
     * @param base     밑
     * @param exponent 지수(0·음수·실수 허용)
     * @return {@code base}<sup>{@code exponent}</sup> ({@link Math#pow}와 동일한 의미)
     */
    public static double power(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    /**
     * 첫 번째 수를 두 번째 수로 나눈 나머지를 구한다.
     *
     * @param a 피제수
     * @param b 제수
     * @return {@code a % b}
     * @throws ArithmeticException {@code b}가 0인 경우
     */
    public static double modulo(double a, double b) {
        if (b == 0.0) {
            throw new ArithmeticException(MODULO_BY_ZERO_MESSAGE);
        }
        return a % b;
    }
}
