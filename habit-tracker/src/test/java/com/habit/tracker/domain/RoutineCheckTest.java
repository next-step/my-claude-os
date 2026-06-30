package com.habit.tracker.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoutineCheck 도메인 단위 테스트
 *
 * 테스트 대상:
 *   - RoutineCheck.of(Routine, LocalDate, boolean): 팩토리 메서드 검증
 *
 * 핵심 불변식:
 *   - completed=true 이면 checkedAt 이 자동 설정된다
 *   - completed=false 이면 checkedAt 은 null 이다
 */
class RoutineCheckTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2024, 1, 15);

    // ── of 팩토리 메서드 ──────────────────────────────────────────────────

    @Test
    @DisplayName("of: completed=true 로 생성하면 checkedAt 이 null 이 아니다")
    void of_완료true면checkedAt이설정된다() {
        // given
        Routine routine = new Routine("물 마시기", "하루 2L", "MON,TUE,WED,THU,FRI,SAT,SUN");

        // when
        RoutineCheck check = RoutineCheck.of(routine, FIXED_DATE, true);

        // then
        assertThat(check.isCompleted()).isTrue();
        assertThat(check.getCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("of: completed=false 로 생성하면 checkedAt 이 null 이다")
    void of_완료false면checkedAt이null이다() {
        // given
        Routine routine = new Routine("물 마시기", null, "MON");

        // when
        RoutineCheck check = RoutineCheck.of(routine, FIXED_DATE, false);

        // then
        assertThat(check.isCompleted()).isFalse();
        assertThat(check.getCheckedAt()).isNull();
    }

    @Test
    @DisplayName("of: 전달한 Routine 객체가 그대로 설정된다")
    void of_전달한루틴객체가그대로설정된다() {
        // given
        Routine routine = new Routine("달리기", "30분", "MON,WED,FRI");

        // when
        RoutineCheck check = RoutineCheck.of(routine, FIXED_DATE, true);

        // then
        assertThat(check.getRoutine()).isSameAs(routine);
    }

    @Test
    @DisplayName("of: 전달한 날짜가 checkDate 에 정확히 설정된다")
    void of_전달한날짜가checkDate에설정된다() {
        // given
        Routine routine = new Routine("독서", null, "SAT,SUN");
        LocalDate targetDate = LocalDate.of(2024, 6, 15);

        // when
        RoutineCheck check = RoutineCheck.of(routine, targetDate, false);

        // then
        assertThat(check.getCheckDate()).isEqualTo(targetDate);
    }

    @Test
    @DisplayName("of: 기본 ID 는 null 이다 (JPA 가 생성 전)")
    void of_id는null이다() {
        // given
        Routine routine = new Routine("명상", null, "MON");

        // when
        RoutineCheck check = RoutineCheck.of(routine, FIXED_DATE, true);

        // then
        assertThat(check.getId()).isNull();
    }
}
