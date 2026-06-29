package com.habit.tracker.service;

import com.habit.tracker.domain.Level;
import com.habit.tracker.domain.Routine;
import com.habit.tracker.domain.RoutineCheck;
import com.habit.tracker.repository.RoutineCheckRepository;
import com.habit.tracker.repository.RoutineRepository;
import com.habit.tracker.service.dto.CheckLevelUpResult;
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
    private final UserStatsService userStatsService;

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

    /**
     * 체크 토글 — 없으면 완료 생성, 있으면 완료/미완료 전환
     *
     * 완료(false→true) 시 XP 적립 로직:
     *   1. xpAwarded=false 이면 +10 XP 지급 후 xpAwarded=true
     *   2. 오늘 예정 루틴 전체 완료 && 오늘 보너스 미수령 → +50 XP 지급
     *   3. XP 지급 전후 레벨 비교 → 레벨업 시 CheckLevelUpResult 에 담아 반환
     *
     * @return 레벨업 발생 여부 및 레벨 정보를 담은 결과
     */
    public CheckLevelUpResult toggleCheck(Long routineId, LocalDate date) {
        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 루틴: " + routineId));

        Optional<RoutineCheck> existing = routineCheckRepository
                .findByRoutineIdAndCheckDate(routineId, date);

        boolean wasCompleted;
        RoutineCheck check;

        if (existing.isPresent()) {
            check = existing.get();
            wasCompleted = check.isCompleted();
            check.setCompleted(!check.isCompleted());
            check.setCheckedAt(check.isCompleted() ? LocalDateTime.now() : null);
        } else {
            check = RoutineCheck.of(routine, date, true);
            routineCheckRepository.save(check);
            wasCompleted = false;
        }

        boolean nowCompleted = check.isCompleted();

        // 체크 해제(완료→미완료) 이면 XP 로직 없이 반환
        if (wasCompleted && !nowCompleted) {
            return CheckLevelUpResult.noChange();
        }

        // 완료(미완료→완료) 인 경우에만 XP 적립 처리
        if (!wasCompleted && nowCompleted) {
            Level prevLevel = userStatsService.getCurrentLevel();

            // 1. 루틴별 XP (중복 방지)
            if (!check.isXpAwarded()) {
                userStatsService.awardXp(10);
                check.setXpAwarded(true);
            }

            // 2. 오늘 전체 완료 보너스 (해당 date 기준으로 판단)
            List<TodayRoutineDto> todayRoutines = getRoutinesForDate(date);
            boolean allCompleted = !todayRoutines.isEmpty()
                    && todayRoutines.stream().allMatch(TodayRoutineDto::isCompleted);

            if (date.equals(LocalDate.now()) && allCompleted && !userStatsService.isTodayBonusAwarded()) {
                userStatsService.awardXp(50);
                userStatsService.markTodayBonusAwarded();
            }

            // 3. 레벨업 감지
            Level newLevel = userStatsService.getCurrentLevel();
            if (prevLevel != null && prevLevel != newLevel) {
                return CheckLevelUpResult.levelUp(prevLevel, newLevel);
            }
        }

        return CheckLevelUpResult.noChange();
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
