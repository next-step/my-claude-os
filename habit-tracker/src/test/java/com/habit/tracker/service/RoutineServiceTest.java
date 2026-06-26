package com.habit.tracker.service;

import com.habit.tracker.domain.Routine;
import com.habit.tracker.domain.RoutineCheck;
import com.habit.tracker.repository.RoutineCheckRepository;
import com.habit.tracker.repository.RoutineRepository;
import com.habit.tracker.service.dto.RoutineForm;
import com.habit.tracker.service.dto.TodayRoutineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * RoutineService 단위 테스트
 *
 * Repository 는 Mockito 로 목킹 → 순수 비즈니스 로직만 검증
 *
 * 설계 주의사항:
 *   getTodayCompletionRate / getDailyCompletionRates 는 내부적으로 LocalDate.now() 를 사용.
 *   요일 필터를 항상 통과하도록 "MON,TUE,WED,THU,FRI,SAT,SUN" 루틴을 공통 픽스처로 사용.
 */
@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    @Mock
    private RoutineRepository routineRepository;

    @Mock
    private RoutineCheckRepository routineCheckRepository;

    @InjectMocks
    private RoutineService routineService;

    /** 모든 요일에 해당하여 어떤 날이든 요일 필터를 통과하는 루틴 */
    private Routine allDaysRoutine1;
    private Routine allDaysRoutine2;

    /** 고정 월요일 날짜 (2024-01-01 = MONDAY) */
    private static final LocalDate MONDAY = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        allDaysRoutine1 = new Routine("물 마시기", "하루 2L", "MON,TUE,WED,THU,FRI,SAT,SUN");
        allDaysRoutine1.setId(1L);

        allDaysRoutine2 = new Routine("독서", "30분", "MON,TUE,WED,THU,FRI,SAT,SUN");
        allDaysRoutine2.setId(2L);
    }

    // ── getRoutinesForDate ────────────────────────────────────────────────

    @Nested
    @DisplayName("getRoutinesForDate")
    class GetRoutinesForDate {

        @Test
        @DisplayName("해당 요일에 스케줄된 루틴만 반환하고, 다른 요일 루틴은 제외한다")
        void 해당요일루틴만반환_다른요일제외() {
            // given
            Routine mondayOnly = new Routine("월요일 루틴", null, "MON");
            mondayOnly.setId(10L);
            Routine tuesdayOnly = new Routine("화요일 루틴", null, "TUE");
            tuesdayOnly.setId(11L);

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(mondayOnly, tuesdayOnly));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(10L, MONDAY))
                    .willReturn(Optional.empty());

            // when
            List<TodayRoutineDto> result = routineService.getRoutinesForDate(MONDAY);

            // then - 화요일 루틴은 월요일에 포함되지 않는다
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("월요일 루틴");
        }

        @Test
        @DisplayName("활성 루틴이 없으면 빈 리스트를 반환한다")
        void 활성루틴없으면빈리스트반환() {
            // given
            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of());

            // when
            List<TodayRoutineDto> result = routineService.getRoutinesForDate(MONDAY);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("완료 체크 기록이 있으면 completed=true 를 반환한다")
        void 완료체크있으면completed_true() {
            // given
            Routine routine = new Routine("월요일 루틴", null, "MON");
            routine.setId(10L);
            RoutineCheck completedCheck = RoutineCheck.of(routine, MONDAY, true);

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(routine));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(10L, MONDAY))
                    .willReturn(Optional.of(completedCheck));

            // when
            List<TodayRoutineDto> result = routineService.getRoutinesForDate(MONDAY);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).isCompleted()).isTrue();
        }

        @Test
        @DisplayName("완료 체크 기록이 없으면 completed=false 를 반환한다")
        void 완료체크없으면completed_false() {
            // given
            Routine routine = new Routine("월요일 루틴", null, "MON");
            routine.setId(10L);

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(routine));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(10L, MONDAY))
                    .willReturn(Optional.empty());

            // when
            List<TodayRoutineDto> result = routineService.getRoutinesForDate(MONDAY);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).isCompleted()).isFalse();
        }

        @Test
        @DisplayName("DTO 에 루틴 이름과 설명이 정확히 담긴다")
        void DTO에루틴정보정확히담김() {
            // given
            Routine routine = new Routine("물 마시기", "하루 2L 목표", "MON");
            routine.setId(10L);

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(routine));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(10L, MONDAY))
                    .willReturn(Optional.empty());

            // when
            List<TodayRoutineDto> result = routineService.getRoutinesForDate(MONDAY);

            // then
            TodayRoutineDto dto = result.get(0);
            assertThat(dto.getName()).isEqualTo("물 마시기");
            assertThat(dto.getDescription()).isEqualTo("하루 2L 목표");
        }
    }

    // ── getTodayCompletionRate ────────────────────────────────────────────

    @Nested
    @DisplayName("getTodayCompletionRate — 경계값 테스트")
    class GetTodayCompletionRate {

        @Test
        @DisplayName("오늘 루틴이 없으면 0을 반환한다")
        void 루틴없으면0() {
            // given
            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of());

            // when
            int rate = routineService.getTodayCompletionRate();

            // then
            assertThat(rate).isZero();
        }

        @Test
        @DisplayName("2개 루틴 모두 완료하면 100을 반환한다")
        void 모두완료면100() {
            // given
            LocalDate today = LocalDate.now();
            RoutineCheck check1 = RoutineCheck.of(allDaysRoutine1, today, true);
            RoutineCheck check2 = RoutineCheck.of(allDaysRoutine2, today, true);

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(allDaysRoutine1, allDaysRoutine2));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(1L, today))
                    .willReturn(Optional.of(check1));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(2L, today))
                    .willReturn(Optional.of(check2));

            // when
            int rate = routineService.getTodayCompletionRate();

            // then
            assertThat(rate).isEqualTo(100);
        }

        @Test
        @DisplayName("2개 루틴 중 1개만 완료하면 50을 반환한다")
        void 절반완료면50() {
            // given
            LocalDate today = LocalDate.now();
            RoutineCheck check1 = RoutineCheck.of(allDaysRoutine1, today, true);

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(allDaysRoutine1, allDaysRoutine2));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(1L, today))
                    .willReturn(Optional.of(check1));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(2L, today))
                    .willReturn(Optional.empty());

            // when
            int rate = routineService.getTodayCompletionRate();

            // then
            assertThat(rate).isEqualTo(50);
        }

        @Test
        @DisplayName("2개 루틴 모두 미완료이면 0을 반환한다")
        void 모두미완료면0() {
            // given
            LocalDate today = LocalDate.now();

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(allDaysRoutine1, allDaysRoutine2));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(1L, today))
                    .willReturn(Optional.empty());
            given(routineCheckRepository.findByRoutineIdAndCheckDate(2L, today))
                    .willReturn(Optional.empty());

            // when
            int rate = routineService.getTodayCompletionRate();

            // then
            assertThat(rate).isZero();
        }
    }

    // ── toggleCheck ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleCheck — 체크 토글 로직")
    class ToggleCheck {

        @Test
        @DisplayName("체크 기록이 없으면 완료 상태로 새 기록을 저장한다")
        void 체크기록없으면완료상태로생성() {
            // given
            given(routineRepository.findById(1L))
                    .willReturn(Optional.of(allDaysRoutine1));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(1L, MONDAY))
                    .willReturn(Optional.empty());

            // when
            routineService.toggleCheck(1L, MONDAY);

            // then
            ArgumentCaptor<RoutineCheck> captor = ArgumentCaptor.forClass(RoutineCheck.class);
            then(routineCheckRepository).should().save(captor.capture());

            RoutineCheck saved = captor.getValue();
            assertThat(saved.isCompleted()).isTrue();
            assertThat(saved.getCheckDate()).isEqualTo(MONDAY);
            assertThat(saved.getCheckedAt()).isNotNull();
        }

        @Test
        @DisplayName("완료 상태 기록이 있으면 미완료로 전환하고 checkedAt 을 null 로 만든다")
        void 완료상태기록있으면미완료로전환() {
            // given
            RoutineCheck existingCheck = RoutineCheck.of(allDaysRoutine1, MONDAY, true);

            given(routineRepository.findById(1L))
                    .willReturn(Optional.of(allDaysRoutine1));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(1L, MONDAY))
                    .willReturn(Optional.of(existingCheck));

            // when
            routineService.toggleCheck(1L, MONDAY);

            // then - save 는 호출되지 않는다 (기존 기록 수정은 JPA dirty checking)
            assertThat(existingCheck.isCompleted()).isFalse();
            assertThat(existingCheck.getCheckedAt()).isNull();
        }

        @Test
        @DisplayName("미완료 상태 기록이 있으면 완료로 전환하고 checkedAt 을 설정한다")
        void 미완료상태기록있으면완료로전환() {
            // given
            RoutineCheck existingCheck = RoutineCheck.of(allDaysRoutine1, MONDAY, false);

            given(routineRepository.findById(1L))
                    .willReturn(Optional.of(allDaysRoutine1));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(1L, MONDAY))
                    .willReturn(Optional.of(existingCheck));

            // when
            routineService.toggleCheck(1L, MONDAY);

            // then
            assertThat(existingCheck.isCompleted()).isTrue();
            assertThat(existingCheck.getCheckedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 루틴 ID 이면 IllegalArgumentException 을 던진다")
        void 존재하지않는루틴이면예외발생() {
            // given
            given(routineRepository.findById(999L))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> routineService.toggleCheck(999L, MONDAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("존재하지 않는 루틴");
        }
    }

    // ── findAllActive ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAllActive")
    class FindAllActive {

        @Test
        @DisplayName("Repository 가 반환하는 활성 루틴 목록을 그대로 반환한다")
        void 활성루틴목록그대로반환() {
            // given
            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(allDaysRoutine1, allDaysRoutine2));

            // when
            List<Routine> result = routineService.findAllActive();

            // then
            assertThat(result)
                    .hasSize(2)
                    .containsExactly(allDaysRoutine1, allDaysRoutine2);
        }

        @Test
        @DisplayName("활성 루틴이 없으면 빈 리스트를 반환한다")
        void 활성루틴없으면빈리스트() {
            // given
            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of());

            // when
            List<Routine> result = routineService.findAllActive();

            // then
            assertThat(result).isEmpty();
        }
    }

    // ── createRoutine ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("createRoutine")
    class CreateRoutine {

        @Test
        @DisplayName("폼 데이터로 Routine 을 생성하고 Repository 에 저장한다")
        void 폼데이터로루틴생성후저장() {
            // given
            RoutineForm form = new RoutineForm();
            form.setName("아침 스트레칭");
            form.setDescription("기상 후 10분");
            form.setDays(List.of("MON", "WED", "FRI"));

            given(routineRepository.save(any(Routine.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            Routine saved = routineService.createRoutine(form);

            // then
            assertThat(saved.getName()).isEqualTo("아침 스트레칭");
            assertThat(saved.getDescription()).isEqualTo("기상 후 10분");
            assertThat(saved.getDaysOfWeek()).isEqualTo("MON,WED,FRI");
            assertThat(saved.isActive()).isTrue(); // 생성 시 기본 활성

            then(routineRepository).should().save(any(Routine.class));
        }

        @Test
        @DisplayName("요일 미선택 시 기본값 전체 요일로 생성된다")
        void 요일미선택시기본전체요일() {
            // given - RoutineForm 기본 생성자는 전체 요일을 days 에 담는다
            RoutineForm form = new RoutineForm();
            form.setName("기본 루틴");

            given(routineRepository.save(any(Routine.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            Routine saved = routineService.createRoutine(form);

            // then
            assertThat(saved.getDaysOfWeek()).isEqualTo("MON,TUE,WED,THU,FRI,SAT,SUN");
        }
    }

    // ── deactivateRoutine ─────────────────────────────────────────────────

    @Nested
    @DisplayName("deactivateRoutine")
    class DeactivateRoutine {

        @Test
        @DisplayName("루틴의 active 를 false 로 변경한다")
        void 루틴active를false로변경() {
            // given
            given(routineRepository.findById(1L))
                    .willReturn(Optional.of(allDaysRoutine1));

            // when
            routineService.deactivateRoutine(1L);

            // then
            assertThat(allDaysRoutine1.isActive()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 루틴 ID 이면 IllegalArgumentException 을 던진다")
        void 존재하지않는루틴이면예외발생() {
            // given
            given(routineRepository.findById(999L))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> routineService.deactivateRoutine(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("존재하지 않는 루틴");
        }
    }

    // ── getDailyCompletionRates ───────────────────────────────────────────

    @Nested
    @DisplayName("getDailyCompletionRates — 통계 계산")
    class GetDailyCompletionRates {

        @Test
        @DisplayName("days=7 이면 오늘 포함 최근 7일 날짜 키를 가진 맵을 반환한다")
        void days7요청시7개날짜맵반환() {
            // given
            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of());

            // when
            Map<LocalDate, Integer> result = routineService.getDailyCompletionRates(7);

            // then
            assertThat(result).hasSize(7);
            assertThat(result).containsKey(LocalDate.now());
            assertThat(result).containsKey(LocalDate.now().minusDays(6));
        }

        @Test
        @DisplayName("루틴이 없는 날은 완료율 0을 반환한다")
        void 루틴없는날은0퍼센트() {
            // given
            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of());

            // when
            Map<LocalDate, Integer> result = routineService.getDailyCompletionRates(3);

            // then
            assertThat(result.values()).allMatch(rate -> rate == 0);
        }

        @Test
        @DisplayName("days=1 일 때 오늘 루틴이 모두 완료되면 완료율 100을 반환한다")
        void 오늘루틴모두완료면100퍼센트() {
            // given — days=1 이면 today 하나만 계산
            LocalDate today = LocalDate.now();
            RoutineCheck check = RoutineCheck.of(allDaysRoutine1, today, true);

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(allDaysRoutine1));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(1L, today))
                    .willReturn(Optional.of(check));

            // when
            Map<LocalDate, Integer> result = routineService.getDailyCompletionRates(1);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(today)).isEqualTo(100);
        }

        @Test
        @DisplayName("days=1 일 때 오늘 루틴 2개 중 1개만 완료되면 완료율 50을 반환한다")
        void 오늘루틴절반완료면50퍼센트() {
            // given
            LocalDate today = LocalDate.now();
            RoutineCheck check = RoutineCheck.of(allDaysRoutine1, today, true);

            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of(allDaysRoutine1, allDaysRoutine2));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(1L, today))
                    .willReturn(Optional.of(check));
            given(routineCheckRepository.findByRoutineIdAndCheckDate(2L, today))
                    .willReturn(Optional.empty());

            // when
            Map<LocalDate, Integer> result = routineService.getDailyCompletionRates(1);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(today)).isEqualTo(50);
        }

        @Test
        @DisplayName("반환된 맵의 날짜는 오래된 순서(오름차순)로 정렬된다")
        void 날짜는오름차순정렬() {
            // given
            given(routineRepository.findByActiveTrueOrderByCreatedAtAsc())
                    .willReturn(List.of());

            // when
            Map<LocalDate, Integer> result = routineService.getDailyCompletionRates(5);

            // then — LinkedHashMap 이므로 삽입 순서(오름차순) 유지
            List<LocalDate> keys = List.copyOf(result.keySet());
            for (int i = 0; i < keys.size() - 1; i++) {
                assertThat(keys.get(i)).isBefore(keys.get(i + 1));
            }
        }
    }
}
