package com.habit.tracker.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routine 도메인 단위 테스트
 *
 * 테스트 대상:
 *   - isScheduledFor(DayOfWeek): 요일 일치 여부 확인
 *   - getDayList(): daysOfWeek 문자열을 리스트로 변환
 *
 * 외부 의존성 없음 — 순수 POJO 테스트
 */
class RoutineTest {

    // ── isScheduledFor ────────────────────────────────────────────────────

    @Test
    @DisplayName("모든 요일 루틴은 어떤 요일에도 true를 반환한다")
    void isScheduledFor_모든요일루틴_어떤요일이든true() {
        // given
        Routine routine = new Routine("매일 운동", null, "MON,TUE,WED,THU,FRI,SAT,SUN");

        // when & then
        for (DayOfWeek dow : DayOfWeek.values()) {
            assertThat(routine.isScheduledFor(dow))
                    .as("요일 %s 는 포함되어야 한다", dow)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("주중(MON-FRI) 루틴은 토요일에 false를 반환한다")
    void isScheduledFor_주중루틴_토요일이면false() {
        // given
        Routine routine = new Routine("주중 독서", null, "MON,TUE,WED,THU,FRI");

        // when
        boolean result = routine.isScheduledFor(DayOfWeek.SATURDAY);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("주중(MON-FRI) 루틴은 일요일에 false를 반환한다")
    void isScheduledFor_주중루틴_일요일이면false() {
        // given
        Routine routine = new Routine("주중 독서", null, "MON,TUE,WED,THU,FRI");

        // when
        boolean result = routine.isScheduledFor(DayOfWeek.SUNDAY);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("주말(SAT,SUN) 루틴은 토요일에 true를 반환한다")
    void isScheduledFor_주말루틴_토요일이면true() {
        // given
        Routine routine = new Routine("주말 달리기", null, "SAT,SUN");

        // when
        boolean result = routine.isScheduledFor(DayOfWeek.SATURDAY);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("주말(SAT,SUN) 루틴은 월요일에 false를 반환한다")
    void isScheduledFor_주말루틴_월요일이면false() {
        // given
        Routine routine = new Routine("주말 달리기", null, "SAT,SUN");

        // when
        boolean result = routine.isScheduledFor(DayOfWeek.MONDAY);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("단일 요일(MON) 루틴은 월요일에만 true, 나머지 6요일은 false를 반환한다")
    void isScheduledFor_단일요일루틴_해당요일만true() {
        // given
        Routine routine = new Routine("월요일 루틴", null, "MON");

        // when & then
        assertThat(routine.isScheduledFor(DayOfWeek.MONDAY)).isTrue();
        assertThat(routine.isScheduledFor(DayOfWeek.TUESDAY)).isFalse();
        assertThat(routine.isScheduledFor(DayOfWeek.WEDNESDAY)).isFalse();
        assertThat(routine.isScheduledFor(DayOfWeek.THURSDAY)).isFalse();
        assertThat(routine.isScheduledFor(DayOfWeek.FRIDAY)).isFalse();
        assertThat(routine.isScheduledFor(DayOfWeek.SATURDAY)).isFalse();
        assertThat(routine.isScheduledFor(DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    @DisplayName("격일 루틴(MON,WED,FRI)은 해당 요일에 true, 나머지는 false를 반환한다")
    void isScheduledFor_격일루틴_해당요일만true() {
        // given
        Routine routine = new Routine("격일 운동", null, "MON,WED,FRI");

        // when & then
        assertThat(routine.isScheduledFor(DayOfWeek.MONDAY)).isTrue();
        assertThat(routine.isScheduledFor(DayOfWeek.TUESDAY)).isFalse();
        assertThat(routine.isScheduledFor(DayOfWeek.WEDNESDAY)).isTrue();
        assertThat(routine.isScheduledFor(DayOfWeek.THURSDAY)).isFalse();
        assertThat(routine.isScheduledFor(DayOfWeek.FRIDAY)).isTrue();
        assertThat(routine.isScheduledFor(DayOfWeek.SATURDAY)).isFalse();
        assertThat(routine.isScheduledFor(DayOfWeek.SUNDAY)).isFalse();
    }

    // ── getDayList ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDayList는 쉼표 구분 문자열을 리스트로 변환한다")
    void getDayList_쉼표구분문자열을리스트로반환() {
        // given
        Routine routine = new Routine("테스트", null, "MON,WED,FRI");

        // when
        List<String> days = routine.getDayList();

        // then
        assertThat(days)
                .hasSize(3)
                .containsExactly("MON", "WED", "FRI");
    }

    @Test
    @DisplayName("getDayList는 단일 요일 문자열을 단일 원소 리스트로 반환한다")
    void getDayList_단일요일문자열을단일원소리스트로반환() {
        // given
        Routine routine = new Routine("테스트", null, "SUN");

        // when
        List<String> days = routine.getDayList();

        // then
        assertThat(days)
                .hasSize(1)
                .containsExactly("SUN");
    }

    @Test
    @DisplayName("getDayList는 전체 7요일 문자열을 7개 원소 리스트로 반환한다")
    void getDayList_전체요일문자열을7개원소리스트로반환() {
        // given
        Routine routine = new Routine("테스트", null, "MON,TUE,WED,THU,FRI,SAT,SUN");

        // when
        List<String> days = routine.getDayList();

        // then
        assertThat(days)
                .hasSize(7)
                .containsExactly("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    }
}
