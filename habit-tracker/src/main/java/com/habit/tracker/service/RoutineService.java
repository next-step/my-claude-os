package com.habit.tracker.service;

import com.habit.tracker.domain.Routine;
import com.habit.tracker.domain.RoutineCheck;
import com.habit.tracker.repository.RoutineCheckRepository;
import com.habit.tracker.repository.RoutineRepository;
import com.habit.tracker.service.dto.RoutineForm;
import com.habit.tracker.service.dto.TodayRoutineDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final RoutineCheckRepository routineCheckRepository;

    // ── 오늘 ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TodayRoutineDto> getTodayRoutines() {
        return getRoutinesForDate(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<TodayRoutineDto> getRoutinesForDate(LocalDate date) {
        List<Routine> actives = routineRepository.findByActiveTrueOrderByCreatedAtAsc();
        return actives.stream()
                .filter(r -> r.isScheduledFor(date.getDayOfWeek()))
                .map(r -> {
                    boolean completed = routineCheckRepository
                            .findByRoutineIdAndCheckDate(r.getId(), date)
                            .map(RoutineCheck::isCompleted)
                            .orElse(false);
                    return new TodayRoutineDto(r, completed);
                })
                .collect(Collectors.toList());
    }

    /** 오늘 완료율 (0~100) */
    @Transactional(readOnly = true)
    public int getTodayCompletionRate() {
        List<TodayRoutineDto> list = getTodayRoutines();
        if (list.isEmpty()) return 0;
        long completed = list.stream().filter(TodayRoutineDto::isCompleted).count();
        return (int) Math.round(completed * 100.0 / list.size());
    }

    /** 체크 토글 — 없으면 완료 생성, 있으면 완료/미완료 전환 */
    public void toggleCheck(Long routineId, LocalDate date) {
        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 루틴: " + routineId));

        Optional<RoutineCheck> existing = routineCheckRepository
                .findByRoutineIdAndCheckDate(routineId, date);

        if (existing.isPresent()) {
            RoutineCheck check = existing.get();
            check.setCompleted(!check.isCompleted());
            check.setCheckedAt(check.isCompleted() ? LocalDateTime.now() : null);
        } else {
            routineCheckRepository.save(RoutineCheck.of(routine, date, true));
        }
    }

    // ── 통계 ───────────────────────────────────────────────────────────────

    /**
     * 최근 N일간 날짜별 완료율 반환
     * 반환: {날짜 → 완료율(0~100)} (날짜 오름차순)
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Integer> getDailyCompletionRates(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1);

        Map<LocalDate, Integer> result = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<TodayRoutineDto> routines = getRoutinesForDate(d);
            if (routines.isEmpty()) {
                result.put(d, 0);
            } else {
                long completed = routines.stream().filter(TodayRoutineDto::isCompleted).count();
                result.put(d, (int) Math.round(completed * 100.0 / routines.size()));
            }
        }
        return result;
    }

    // ── 루틴 CRUD ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Routine> findAllActive() {
        return routineRepository.findByActiveTrueOrderByCreatedAtAsc();
    }

    public Routine createRoutine(RoutineForm form) {
        Routine routine = new Routine(form.getName(), form.getDescription(), form.getDaysOfWeek());
        return routineRepository.save(routine);
    }

    public void deactivateRoutine(Long id) {
        Routine routine = routineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 루틴: " + id));
        routine.setActive(false);
    }
}
