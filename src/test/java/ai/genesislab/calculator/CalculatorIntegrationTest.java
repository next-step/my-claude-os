package ai.genesislab.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Calculator} 통합 테스트.
 *
 * <p>단일 연산이 아니라 여러 연산을 조합한 실제 사용 흐름을 검증한다.
 * 한 연산의 출력이 다음 연산의 입력으로 흘러가는 시나리오를 다룬다.</p>
 */
@DisplayName("Calculator 통합 테스트 — 연산 조합 흐름")
class CalculatorIntegrationTest {

    private static final double DELTA = 1e-9;

    @Test
    @DisplayName("(a + b) / c 흐름: 덧셈 결과를 나눗셈에 투입")
    void addThenDivide_flow() {
        double sum = Calculator.add(4.0, 6.0);          // 10
        double result = Calculator.divide(sum, 2.0);    // 5
        assertEquals(5.0, result, DELTA);
    }

    @Test
    @DisplayName("((a + b) / c)^d 흐름: 덧셈→나눗셈→거듭제곱 연쇄")
    void addDivideThenPower_flow() {
        double sum = Calculator.add(2.0, 6.0);          // 8
        double quotient = Calculator.divide(sum, 4.0);  // 2
        double powered = Calculator.power(quotient, 3.0); // 8
        assertEquals(8.0, powered, DELTA);
    }

    @Test
    @DisplayName("평균 계산 흐름: 합을 개수로 나눈다")
    void average_flow() {
        double total = Calculator.add(Calculator.add(10.0, 20.0), 30.0); // 60
        double average = Calculator.divide(total, 3.0);                   // 20
        assertEquals(20.0, average, DELTA);
    }

    @Test
    @DisplayName("나머지→곱셈→뺄셈 흐름: 혼합 연산")
    void moduloMultiplySubtract_flow() {
        double remainder = Calculator.modulo(17.0, 5.0);     // 2
        double scaled = Calculator.multiply(remainder, 4.0); // 8
        double result = Calculator.subtract(scaled, 3.0);    // 5
        assertEquals(5.0, result, DELTA);
    }

    @Test
    @DisplayName("피타고라스 흐름: sqrt(a^2 + b^2) = power(sum, 0.5)")
    void pythagoras_flow() {
        double a2 = Calculator.power(3.0, 2.0);          // 9
        double b2 = Calculator.power(4.0, 2.0);          // 16
        double hypotenuse = Calculator.power(Calculator.add(a2, b2), 0.5); // 5
        assertEquals(5.0, hypotenuse, DELTA);
    }

    @Test
    @DisplayName("예외 전파 흐름: 중간 나눗셈의 0이 흐름 전체를 중단시킨다")
    void divideByZeroInChain_propagatesException() {
        double sum = Calculator.add(3.0, 3.0); // 6
        assertThrows(ArithmeticException.class, () -> {
            double bad = Calculator.divide(sum, 0.0); // 예외 발생 지점
            Calculator.power(bad, 2.0);               // 도달하지 않음
        });
    }

    @Test
    @DisplayName("복리 흐름: principal * (1 + rate)^periods")
    void compoundInterest_flow() {
        double principal = 1000.0;
        double rate = 0.05;
        double base = Calculator.add(1.0, rate);            // 1.05
        double growth = Calculator.power(base, 2.0);        // 1.1025
        double amount = Calculator.multiply(principal, growth); // 1102.5
        assertEquals(1102.5, amount, DELTA);
    }
}
