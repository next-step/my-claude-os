package com.habit.tracker.web;

import com.habit.tracker.service.RoutineService;
import com.habit.tracker.service.UserStatsService;
import com.habit.tracker.service.dto.CheckLevelUpResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final RoutineService routineService;
    private final UserStatsService userStatsService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("todayRoutines", routineService.getTodayRoutines());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("completionRate", routineService.getTodayCompletionRate());

        // XP / 레벨 속성
        model.addAttribute("userLevel", userStatsService.getCurrentLevel());
        model.addAttribute("xpInCurrentLevel", userStatsService.getXpInCurrentLevel());
        model.addAttribute("xpToNextLevel", userStatsService.getXpToNextLevel());
        model.addAttribute("xpProgressPercent", userStatsService.getXpProgressPercent());

        // 레벨업 배너 기본값 설정 (플래시 속성 없는 경우 대비)
        if (!model.containsAttribute("leveledUp")) {
            model.addAttribute("leveledUp", false);
        }

        return "index";
    }

    @PostMapping("/check/{routineId}")
    public String toggleCheck(@PathVariable Long routineId,
                              @RequestParam(defaultValue = "") String returnDate,
                              RedirectAttributes redirectAttributes) {
        LocalDate date = returnDate.isBlank() ? LocalDate.now() : LocalDate.parse(returnDate);
        CheckLevelUpResult result = routineService.toggleCheck(routineId, date);

        // 오늘 홈으로 돌아가는 경우에만 레벨업 배너 전달
        if (returnDate.isBlank() && result.isLeveledUp()) {
            redirectAttributes.addFlashAttribute("leveledUp", true);
            redirectAttributes.addFlashAttribute("newLevelName", result.getNewLevel().getName());
            redirectAttributes.addFlashAttribute("newLevelEmoji", result.getNewLevel().getEmoji());
            redirectAttributes.addFlashAttribute("prevLevelName", result.getPrevLevel().getName());
            redirectAttributes.addFlashAttribute("prevLevelEmoji", result.getPrevLevel().getEmoji());
            redirectAttributes.addFlashAttribute("newLevel", result.getNewLevel());
            redirectAttributes.addFlashAttribute("prevLevel", result.getPrevLevel());
        }

        return returnDate.isBlank() ? "redirect:/" : "redirect:/history?date=" + returnDate;
    }

    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("rates7", routineService.getDailyCompletionRates(7));
        model.addAttribute("rates30", routineService.getDailyCompletionRates(30));
        model.addAttribute("today", LocalDate.now());
        return "stats/index";
    }

    @GetMapping("/history")
    public String history(@RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          Model model) {
        LocalDate target = date != null ? date : LocalDate.now();
        model.addAttribute("target", target);
        model.addAttribute("routines", routineService.getRoutinesForDate(target));
        model.addAttribute("prev", target.minusDays(1));
        model.addAttribute("next", target.plusDays(1));
        model.addAttribute("isToday", target.equals(LocalDate.now()));
        return "history/index";
    }
}
