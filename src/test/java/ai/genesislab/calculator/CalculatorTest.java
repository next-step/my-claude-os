package ai.genesislab.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link Calculator} 단위 테스트.
 *
 * <p>각 연산의 정상·경계·예외 케이스를 검증한다. 실수 비교에는 허용 오차(delta)를 사용한다.</p>
 */
@DisplayName("Calculator 단위 테스트")
class CalculatorTest {

    /** 실수 비교 허용 오차. */
    private static final double DELTA = 1e-9;

    @Nested
    @DisplayName("add(a, b) — 덧셈")
    class Add {

        @Test
        @DisplayName("정상: 두 양수를 더한다")
        void add_twoPositives_returnsSum() {
            assertEquals(5.0, Calculator.add(2.0, 3.0), DELTA);
        }

        @ParameterizedTest(name = "add({0}, {1}) = {2}")
        @DisplayName("경계: 0·음수·실수 조합")
        @CsvSource({
                "0, 0, 0",
                "-2, -3, -5",
                "-5, 5, 0",
                "0.1, 0.2, 0.3",
                "2.5, -1.5, 1.0"
        })
        void add_boundaries_returnsSum(double a, double b, double expected) {
            assertEquals(expected, Calculator.add(a, b), DELTA);
        }

        @Test
        @DisplayName("경계: 큰 수의 덧셈")
        void add_largeNumbers_returnsSum() {
            assertEquals(2.0e15, Calculator.add(1.0e15, 1.0e15), DELTA);
        }
    }

    @Nested
    @DisplayName("subtract(a, b) — 뺄셈")
    class Subtract {

        @Test
        @DisplayName("정상: 두 수의 차")
        void subtract_twoNumbers_returnsDifference() {
            assertEquals(2.0, Calculator.subtract(5.0, 3.0), DELTA);
        }

        @ParameterizedTest(name = "subtract({0}, {1}) = {2}")
        @DisplayName("경계: 0·음수·실수 조합")
        @CsvSource({
                "0, 0, 0",
                "-2, -3, 1",
                "3, -3, 6",
                "0.3, 0.1, 0.2"
        })
        void subtract_boundaries_returnsDifference(double a, double b, double expected) {
            assertEquals(expected, Calculator.subtract(a, b), DELTA);
        }
    }

    @Nested
    @DisplayName("multiply(a, b) — 곱셈")
    class Multiply {

        @Test
        @DisplayName("정상: 두 양수의 곱")
        void multiply_twoPositives_returnsProduct() {
            assertEquals(12.0, Calculator.multiply(3.0, 4.0), DELTA);
        }

        @ParameterizedTest(name = "multiply({0}, {1}) = {2}")
        @DisplayName("경계: 0·음수·실수 조합")
        @CsvSource({
                "0, 5, 0",
                "-3, 4, -12",
                "-3, -4, 12",
                "0.5, 0.5, 0.25"
        })
        void multiply_boundaries_returnsProduct(double a, double b, double expected) {
            assertEquals(expected, Calculator.multiply(a, b), DELTA);
        }
    }

    @Nested
    @DisplayName("divide(a, b) — 나눗셈")
    class Divide {

        @Test
        @DisplayName("정상: 두 수의 몫")
        void divide_twoNumbers_returnsQuotient() {
            assertEquals(2.5, Calculator.divide(5.0, 2.0), DELTA);
        }

        @Test
        @DisplayName("경계: 음수/실수 나눗셈")
        void divide_negativeAndReal_returnsQuotient() {
            assertEquals(-2.5, Calculator.divide(-5.0, 2.0), DELTA);
            assertEquals(0.5, Calculator.divide(0.2, 0.4), DELTA);
        }

        @Test
        @DisplayName("경계: 0을 0이 아닌 수로 나누면 0")
        void divide_zeroNumerator_returnsZero() {
            assertEquals(0.0, Calculator.divide(0.0, 5.0), DELTA);
        }

        @Test
        @DisplayName("예외: b가 0이면 ArithmeticException과 명확한 메시지")
        void divide_byZero_throwsArithmeticException() {
            ArithmeticException ex =
                    assertThrows(ArithmeticException.class, () -> Calculator.divide(5.0, 0.0));
            assertEquals(Calculator.DIVIDE_BY_ZERO_MESSAGE, ex.getMessage());
        }

        @Test
        @DisplayName("예외: 0을 0으로 나눠도 ArithmeticException (NaN을 반환하지 않음)")
        void divide_zeroByZero_throwsArithmeticException() {
            assertThrows(ArithmeticException.class, () -> Calculator.divide(0.0, 0.0));
        }
    }

    @Nested
    @DisplayName("power(base, exponent) — 거듭제곱")
    class Power {

        @Test
        @DisplayName("정상: 양의 정수 지수")
        void power_positiveExponent_returnsPower() {
            assertEquals(8.0, Calculator.power(2.0, 3.0), DELTA);
        }

        @Test
        @DisplayName("경계: 지수 0이면 1")
        void power_zeroExponent_returnsOne() {
            assertEquals(1.0, Calculator.power(5.0, 0.0), DELTA);
            assertEquals(1.0, Calculator.power(0.0, 0.0), DELTA);
        }

        @Test
        @DisplayName("경계: 음수 지수이면 역수")
        void power_negativeExponent_returnsReciprocal() {
            assertEquals(0.25, Calculator.power(2.0, -2.0), DELTA);
        }

        @Test
        @DisplayName("경계: 실수 지수(제곱근)")
        void power_fractionalExponent_returnsRoot() {
            assertEquals(3.0, Calculator.power(9.0, 0.5), DELTA);
        }

        @Test
        @DisplayName("경계: 음수 밑의 정수 지수")
        void power_negativeBase_returnsSignedPower() {
            assertEquals(-8.0, Calculator.power(-2.0, 3.0), DELTA);
            assertEquals(4.0, Calculator.power(-2.0, 2.0), DELTA);
        }
    }

    @Nested
    @DisplayName("modulo(a, b) — 나머지")
    class Modulo {

        @Test
        @DisplayName("정상: 양수 나머지")
        void modulo_positive_returnsRemainder() {
            assertEquals(1.0, Calculator.modulo(10.0, 3.0), DELTA);
        }

        @Test
        @DisplayName("경계: 나누어떨어지면 0")
        void modulo_exactDivision_returnsZero() {
            assertEquals(0.0, Calculator.modulo(9.0, 3.0), DELTA);
        }

        @Test
        @DisplayName("경계: 음수 피제수의 나머지(부호는 피제수를 따른다)")
        void modulo_negativeDividend_followsDividendSign() {
            assertEquals(-1.0, Calculator.modulo(-10.0, 3.0), DELTA);
        }

        @Test
        @DisplayName("경계: 실수 나머지")
        void modulo_realNumbers_returnsRemainder() {
            assertEquals(0.5, Calculator.modulo(5.5, 1.0), DELTA);
        }

        @Test
        @DisplayName("예외: b가 0이면 ArithmeticException과 명확한 메시지")
        void modulo_byZero_throwsArithmeticException() {
            ArithmeticException ex =
                    assertThrows(ArithmeticException.class, () -> Calculator.modulo(10.0, 0.0));
            assertEquals(Calculator.MODULO_BY_ZERO_MESSAGE, ex.getMessage());
        }
    }

    @Test
    @DisplayName("설계: 유틸리티 클래스는 인스턴스화할 수 없다")
    void calculator_cannotBeInstantiated() throws Exception {
        var constructor = Calculator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                constructor::newInstance);
        assertSame(AssertionError.class, thrown.getCause().getClass());
    }
}
