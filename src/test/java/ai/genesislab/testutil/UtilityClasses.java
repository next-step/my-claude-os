package ai.genesislab.testutil;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * 유틸리티(정적 메서드 모음) 클래스의 공통 테스트 헬퍼.
 *
 * <p>저장소 컨벤션상 상태 없는 모음 클래스는 {@code final class} + private 생성자에서
 * {@link AssertionError}를 던져 인스턴스화를 막는다. 이 불변식을 리플렉션으로 검증하는 동일한 6줄이
 * 여러 테스트에 복붙되어 있어, 여기로 공통화한다.</p>
 */
public final class UtilityClasses {

    private UtilityClasses() {
        throw new AssertionError("No ai.genesislab.testutil.UtilityClasses instances for you!");
    }

    /**
     * 주어진 유틸리티 클래스가 인스턴스화될 수 없음을 단언한다(private 생성자가 {@link AssertionError}를 던짐).
     *
     * @param type 검증 대상 유틸리티 클래스
     * @throws Exception 선언된 기본 생성자를 찾지 못하는 등 리플렉션 자체가 실패한 경우
     */
    public static void assertNotInstantiable(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                constructor::newInstance);
        assertSame(AssertionError.class, thrown.getCause().getClass());
    }
}
