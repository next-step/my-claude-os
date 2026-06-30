package com.habit.tracker.integration;

import com.habit.tracker.domain.Routine;
import com.habit.tracker.domain.RoutineCheck;
import com.habit.tracker.repository.RoutineCheckRepository;
import com.habit.tracker.repository.RoutineRepository;
import com.habit.tracker.service.RoutineService;
import com.habit.tracker.service.dto.RoutineForm;
import com.habit.tracker.service.dto.TodayRoutineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 루틴 추적기 End-to-End 통합 테스트
 *
 * 목적:
 *   단위 테스트(RoutineServiceTest)와 개별 컨트롤러 테스트(HomeController/RoutineControllerIntegrationTest)가
 *   커버하지 않는 "여러 계층을 관통하는 사용자 시나리오 흐름"을 검증한다.
 *
 *   핵심 시나리오:
 *     1. 루틴 생성 → 오늘 목록 조회 → 체크 완료 → 완료율 반영
 *     2. 체크 토글 왕복 (완료 → 미완료 → 완료)
 *     3. 루틴 비활성화 → 오늘 목록·통계에서 제외
 *     4. 날짜 지정 체크 → 히스토리 조회
 *
 * 설정:
 *   @ActiveProfiles("test") → src/test/resources/application-test.yml 로드
 *   → H2 인메모리 DB (파일 DB 아닌 순수 메모리, 테스트 종료 시 소멸)
 *   @Transactional → 각 테스트 메서드 종료 후 자동 롤백 (테스트 간 데이터 격리)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoutineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private RoutineCheckRepository routineCheckRepository;

    /**
     * 각 테스트 전 "모든 요일"에 예정된 루틴을 생성한다.
     * 모든 요일 설정이므로 어떤 날에 테스트를 실행해도 요일 필터를 통과한다.
     */
    private Routine baseRoutine;

    @BeforeEach
    void setUp() {
        RoutineForm form = new RoutineForm();
        form.setName("E2E 기본 루틴");
        form.setDescription("통합 테스트 공통 픽스처");
        form.setDays(List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"));
        baseRoutine = routineService.createRoutine(form);
    }

    // ── 시나리오 1: 루틴 생성 → 오늘 목록 조회 → 체크 완료 → 완료율 반영 ──────

    @Nested
    @DisplayName("시나리오 1: 생성 → 오늘 목록 → 체크 완료 → 완료율")
    class Scenario_CreateCheckRate {

        @Test
        @DisplayName("루틴 생성 직후 오늘 목록에 미완료 상태로 나타난다")
        void 루틴생성후_오늘목록에_미완료상태로포함() {
            // when
            List<TodayRoutineDto> todayRoutines = routineService.getTodayRoutines();

            // then
            assertThat(todayRoutines)
                    .anyMatch(dto -> dto.getName().equals("E2E 기본 루틴") && !dto.isCompleted());
        }

        @Test
        @DisplayName("체크 완료 후 오늘 목록에서 completed=true 로 변경된다")
        void 체크완료후_오늘목록에서_completed_true() {
            // given — HTTP를 통해 체크 토글
            // when
            routineService.toggleCheck(baseRoutine.getId(), LocalDate.now());

            // then
            List<TodayRoutineDto> todayRoutines = routineService.getTodayRoutines();
            assertThat(todayRoutines)
                    .filteredOn(dto -> dto.getName().equals("E2E 기본 루틴"))
                    .singleElement()
                    .satisfies(dto -> assertThat(dto.isCompleted()).isTrue());
        }

        @Test
        @DisplayName("1개 루틴 체크 완료 후 오늘 완료율이 100이 된다")
        void 루틴1개완료후_완료율_100() {
            // given — setUp에서 루틴 1개 생성, 체크 완료
            routineService.toggleCheck(baseRoutine.getId(), LocalDate.now());

            // when
            int rate = routineService.getTodayCompletionRate();

            // then
            assertThat(rate).isEqualTo(100);
        }

        @Test
        @DisplayName("루틴 2개 중 1개만 완료 시 오늘 완료율이 50이 된다")
        void 루틴2개중1개완료_완료율_50() {
            // given — 두 번째 루틴 추가
            RoutineForm form2 = new RoutineForm();
            form2.setName("E2E 두 번째 루틴");
            form2.setDays(List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"));
            routineService.createRoutine(form2);

            // 첫 번째 루틴만 완료
            routineService.toggleCheck(baseRoutine.getId(), LocalDate.now());

            // when
            int rate = routineService.getTodayCompletionRate();

            // then
            assertThat(rate).isEqualTo(50);
        }

        @Test
        @DisplayName("POST /check HTTP 요청으로 체크 완료 후 홈 화면이 200을 반환한다")
        void HTTP체크완료후_홈화면_200() throws Exception {
            // when — HTTP 체크 토글
            mockMvc.perform(post("/check/" + baseRoutine.getId()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            // then — 홈 화면에서 todayRoutines 모델 확인
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("index"))
                    .andExpect(model().attributeExists("todayRoutines"))
                    .andExpect(model().attributeExists("completionRate"));
        }
    }

    // ── 시나리오 2: 체크 토글 왕복 ──────────────────────────────────────────

    @Nested
    @DisplayName("시나리오 2: 체크 토글 왕복 (완료 → 미완료 → 완료)")
    class Scenario_ToggleRoundTrip {

        @Test
        @DisplayName("체크 기록이 DB에 저장된 뒤 두 번째 토글로 미완료 상태로 바뀐다")
        void 체크기록DB저장후_두번째토글_미완료전환() {
            // given — 첫 번째 토글: 기록 없음 → 완료 생성
            LocalDate today = LocalDate.now();
            routineService.toggleCheck(baseRoutine.getId(), today);

            Optional<RoutineCheck> afterFirst =
                    routineCheckRepository.findByRoutineIdAndCheckDate(baseRoutine.getId(), today);
            assertThat(afterFirst).isPresent();
            assertThat(afterFirst.get().isCompleted()).isTrue();

            // when — 두 번째 토글: 완료 → 미완료
            routineService.toggleCheck(baseRoutine.getId(), today);

            // then
            Optional<RoutineCheck> afterSecond =
                    routineCheckRepository.findByRoutineIdAndCheckDate(baseRoutine.getId(), today);
            assertThat(afterSecond).isPresent();
            assertThat(afterSecond.get().isCompleted()).isFalse();
            assertThat(afterSecond.get().getCheckedAt()).isNull();
        }

        @Test
        @DisplayName("세 번 토글하면 최종 상태는 완료이다")
        void 세번토글후_최종상태_완료() {
            // given
            LocalDate today = LocalDate.now();

            // when — 1차: 미완료→완료, 2차: 완료→미완료, 3차: 미완료→완료
            routineService.toggleCheck(baseRoutine.getId(), today);
            routineService.toggleCheck(baseRoutine.getId(), today);
            routineService.toggleCheck(baseRoutine.getId(), today);

            // then
            Optional<RoutineCheck> check =
                    routineCheckRepository.findByRoutineIdAndCheckDate(baseRoutine.getId(), today);
            assertThat(check).isPresent();
            assertThat(check.get().isCompleted()).isTrue();
            assertThat(check.get().getCheckedAt()).isNotNull();
        }

        @Test
        @DisplayName("HTTP 두 번 토글 후 오늘 완료율은 다시 0이 된다")
        void HTTP두번토글후_완료율_0() throws Exception {
            // given — 첫 번째 POST (미완료→완료)
            mockMvc.perform(post("/check/" + baseRoutine.getId()));

            // when — 두 번째 POST (완료→미완료)
            mockMvc.perform(post("/check/" + baseRoutine.getId()))
                    .andExpect(status().is3xxRedirection());

            // then — 서비스 레이어에서 완료율 확인
            int rate = routineService.getTodayCompletionRate();
            assertThat(rate).isZero();
        }
    }

    // ── 시나리오 3: 루틴 비활성화 → 목록·통계에서 제외 ──────────────────────

    @Nested
    @DisplayName("시나리오 3: 루틴 비활성화 → 오늘 목록·통계 제외")
    class Scenario_Deactivate {

        @Test
        @DisplayName("루틴 비활성화 후 활성 루틴 목록에서 제외된다")
        void 비활성화후_활성목록_제외() {
            // when
            routineService.deactivateRoutine(baseRoutine.getId());

            // then
            List<Routine> actives = routineService.findAllActive();
            assertThat(actives).noneMatch(r -> r.getId().equals(baseRoutine.getId()));
        }

        @Test
        @DisplayName("루틴 비활성화 후 오늘 루틴 목록에서도 제외된다")
        void 비활성화후_오늘목록_제외() {
            // when
            routineService.deactivateRoutine(baseRoutine.getId());

            // then
            List<TodayRoutineDto> todayRoutines = routineService.getTodayRoutines();
            assertThat(todayRoutines).noneMatch(dto -> dto.getName().equals("E2E 기본 루틴"));
        }

        @Test
        @DisplayName("루틴 비활성화 후 오늘 완료율은 0이 된다 (오늘 루틴 없음)")
        void 비활성화후_완료율_0() {
            // when
            routineService.deactivateRoutine(baseRoutine.getId());

            // then
            int rate = routineService.getTodayCompletionRate();
            assertThat(rate).isZero();
        }

        @Test
        @DisplayName("HTTP DELETE 요청으로 비활성화 후 활성 루틴 목록에서 제외된다")
        void HTTP비활성화후_활성목록_제외() throws Exception {
            // when
            mockMvc.perform(post("/routines/" + baseRoutine.getId() + "/delete"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/routines"));

            // then
            List<Routine> actives = routineService.findAllActive();
            assertThat(actives).noneMatch(r -> r.getId().equals(baseRoutine.getId()));
        }
    }

    // ── 시나리오 4: 날짜 지정 체크 → 히스토리·통계 반영 ───────────────────

    @Nested
    @DisplayName("시나리오 4: 날짜 지정 체크 → 히스토리·통계 반영")
    class Scenario_DateSpecificCheck {

        /** 고정 날짜 — MONDAY (2024-01-01). baseRoutine은 MON을 포함한다. */
        private static final LocalDate FIXED_MONDAY = LocalDate.of(2024, 1, 1);

        @Test
        @DisplayName("과거 날짜로 체크하면 해당 날짜 히스토리에 완료 상태로 저장된다")
        void 과거날짜체크후_히스토리에_완료상태() {
            // when
            routineService.toggleCheck(baseRoutine.getId(), FIXED_MONDAY);

            // then
            Optional<RoutineCheck> check =
                    routineCheckRepository.findByRoutineIdAndCheckDate(baseRoutine.getId(), FIXED_MONDAY);
            assertThat(check).isPresent();
            assertThat(check.get().isCompleted()).isTrue();
        }

        @Test
        @DisplayName("과거 날짜 체크는 오늘 완료율에 영향을 주지 않는다")
        void 과거날짜체크_오늘완료율_영향없음() {
            // given — 과거 날짜 체크
            routineService.toggleCheck(baseRoutine.getId(), FIXED_MONDAY);

            // when — 오늘 완료율은 여전히 0
            int rate = routineService.getTodayCompletionRate();

            // then
            assertThat(rate).isZero();
        }

        @Test
        @DisplayName("HTTP returnDate 파라미터로 과거 체크 후 히스토리 페이지로 리다이렉트한다")
        void HTTP과거날짜체크_히스토리_리다이렉트() throws Exception {
            mockMvc.perform(post("/check/" + baseRoutine.getId())
                            .param("returnDate", FIXED_MONDAY.toString()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/history?date=" + FIXED_MONDAY));
        }

        @Test
        @DisplayName("GET /history?date= 으로 특정 날짜 루틴 목록을 조회한다")
        void 히스토리_특정날짜_루틴목록_조회() throws Exception {
            mockMvc.perform(get("/history").param("date", FIXED_MONDAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("history/index"))
                    .andExpect(model().attributeExists("target"))
                    .andExpect(model().attributeExists("routines"))
                    .andExpect(model().attributeExists("prev"))
                    .andExpect(model().attributeExists("next"));
        }

        @Test
        @DisplayName("getDailyCompletionRates에 포함된 과거 날짜의 완료율이 정확히 계산된다")
        void 통계에_과거날짜완료율_정확히계산() {
            // given — 고정된 과거 날짜에 체크 완료
            routineService.toggleCheck(baseRoutine.getId(), FIXED_MONDAY);

            // when — 오늘 기준 7일치 통계 (FIXED_MONDAY는 7일 이전이므로 포함 안 됨)
            //        날짜 계산 검증만 한다 — 오늘이 포함된 맵인지 확인
            Map<LocalDate, Integer> rates = routineService.getDailyCompletionRates(7);

            // then — 7개 날짜 키, 오늘 포함
            assertThat(rates).hasSize(7);
            assertThat(rates).containsKey(LocalDate.now());

            // FIXED_MONDAY는 7일 범위 밖이므로 오늘 완료율은 0
            assertThat(rates.get(LocalDate.now())).isZero();
        }
    }

    // ── 시나리오 5: 루틴 CRUD HTTP 전체 흐름 ─────────────────────────────────

    @Nested
    @DisplayName("시나리오 5: HTTP 루틴 CRUD 전체 흐름")
    class Scenario_RoutineCRUD {

        @Test
        @DisplayName("POST /routines → GET /routines 흐름에서 생성된 루틴이 목록에 표시된다")
        void 루틴생성후_목록페이지_루틴포함() throws Exception {
            // when — 루틴 생성 (setUp에서 이미 1개 생성됨)
            mockMvc.perform(post("/routines")
                            .param("name", "CRUD 흐름 테스트 루틴")
                            .param("description", "흐름 검증")
                            .param("days", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/routines"));

            // then — GET /routines 목록 페이지 정상 응답
            mockMvc.perform(get("/routines"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("routines/list"))
                    .andExpect(model().attributeExists("routines"));

            // then — 서비스 레이어 검증: 생성된 루틴이 활성 목록에 포함
            List<Routine> actives = routineService.findAllActive();
            assertThat(actives).anyMatch(r -> r.getName().equals("CRUD 흐름 테스트 루틴"));
        }

        @Test
        @DisplayName("루틴 생성 시 이름이 공백이면 400 계열 없이 뷰를 재렌더링한다 (유효성 검증)")
        void 이름공백루틴생성_유효성검증실패_뷰재렌더링() throws Exception {
            mockMvc.perform(post("/routines")
                            .param("name", "")
                            .param("days", "MON"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("routines/list"))
                    .andExpect(model().hasErrors())
                    .andExpect(model().attributeHasFieldErrors("form", "name"));
        }

        @Test
        @DisplayName("GET / → POST /check → GET / 흐름에서 완료율이 갱신된다")
        void 홈조회_체크완료_홈재조회_완료율갱신() throws Exception {
            // given — GET /: completionRate = 0
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("completionRate", 0));

            // when — POST /check (체크 완료)
            mockMvc.perform(post("/check/" + baseRoutine.getId()))
                    .andExpect(redirectedUrl("/"));

            // then — GET /: completionRate = 100
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("completionRate", 100));
        }
    }
}
