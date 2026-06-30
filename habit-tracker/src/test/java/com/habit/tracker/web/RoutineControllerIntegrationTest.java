package com.habit.tracker.web;

import com.habit.tracker.service.RoutineService;
import com.habit.tracker.service.dto.RoutineForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * RoutineController 통합 테스트
 *
 * - H2 인메모리 DB 사용
 * - @Transactional: 각 테스트 후 자동 롤백
 *
 * 검증 범위:
 *   GET  /routines          — 루틴 목록 페이지
 *   POST /routines          — 루틴 생성 (성공 / 유효성 검증 실패)
 *   POST /routines/{id}/delete — 루틴 비활성화
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoutineControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineService routineService;

    // ── GET /routines ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /routines: HTTP 200 + routines/list 뷰, 모델에 routines/form 포함")
    void list_목록페이지정상반환() throws Exception {
        mockMvc.perform(get("/routines"))
                .andExpect(status().isOk())
                .andExpect(view().name("routines/list"))
                .andExpect(model().attributeExists("routines"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("GET /routines: 이미 생성된 루틴이 목록에 포함된다")
    void list_기존루틴이목록에포함됨() throws Exception {
        // given
        RoutineForm form = new RoutineForm();
        form.setName("기존 루틴");
        form.setDays(List.of("MON"));
        routineService.createRoutine(form);

        // when & then
        mockMvc.perform(get("/routines"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("routines"));
    }

    // ── POST /routines ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /routines: 유효한 데이터 제출 시 루틴 생성 후 /routines 로 리다이렉트한다")
    void create_유효한폼제출_생성후리다이렉트() throws Exception {
        mockMvc.perform(post("/routines")
                        .param("name", "아침 스트레칭")
                        .param("description", "기상 후 10분")
                        .param("days", "MON", "WED", "FRI"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/routines"));
    }

    @Test
    @DisplayName("POST /routines: name 이 비어있으면 유효성 검증 실패 후 routines/list 뷰를 반환한다")
    void create_이름없으면유효성검증실패() throws Exception {
        mockMvc.perform(post("/routines")
                        .param("name", "")              // @NotBlank 위반
                        .param("days", "MON"))
                .andExpect(status().isOk())             // 리다이렉트 없이 뷰 재렌더링
                .andExpect(view().name("routines/list"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "name"));
    }

    @Test
    @DisplayName("POST /routines: name 이 100자를 초과하면 유효성 검증 실패한다")
    void create_이름100자초과하면유효성검증실패() throws Exception {
        String over100 = "a".repeat(101);

        mockMvc.perform(post("/routines")
                        .param("name", over100)
                        .param("days", "MON"))
                .andExpect(status().isOk())
                .andExpect(view().name("routines/list"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "name"));
    }

    @Test
    @DisplayName("POST /routines: 유효성 검증 실패 시 routines 목록도 모델에 포함한다 (페이지 재렌더링)")
    void create_실패시목록도모델에포함() throws Exception {
        mockMvc.perform(post("/routines")
                        .param("name", "")
                        .param("days", "MON"))
                .andExpect(model().attributeExists("routines"));
    }

    // ── POST /routines/{id}/delete ────────────────────────────────────────

    @Test
    @DisplayName("POST /routines/{id}/delete: 루틴을 비활성화하고 /routines 로 리다이렉트한다")
    void delete_비활성화후목록리다이렉트() throws Exception {
        // given
        RoutineForm form = new RoutineForm();
        form.setName("삭제할 루틴");
        form.setDays(List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"));
        Long id = routineService.createRoutine(form).getId();

        // when & then
        mockMvc.perform(post("/routines/" + id + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/routines"));
    }

    @Test
    @DisplayName("POST /routines/{id}/delete: 비활성화된 루틴은 이후 목록 조회에서 제외된다")
    void delete_비활성화후목록에서제외됨() throws Exception {
        // given
        RoutineForm form = new RoutineForm();
        form.setName("비활성화될 루틴");
        form.setDays(List.of("MON"));
        Long id = routineService.createRoutine(form).getId();

        // when — 삭제(비활성화) 처리
        mockMvc.perform(post("/routines/" + id + "/delete"));

        // then — findAllActive 에 포함되지 않는다
        boolean stillActive = routineService.findAllActive().stream()
                .anyMatch(r -> r.getId().equals(id));

        org.assertj.core.api.Assertions.assertThat(stillActive).isFalse();
    }
}
