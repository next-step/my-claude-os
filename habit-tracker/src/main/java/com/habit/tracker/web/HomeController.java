package com.habit.tracker.web;

import com.habit.tracker.service.RoutineService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final RoutineService routineService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("todayRoutines", routineService.getTodayRoutines());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("completionRate", routineService.getTodayCompletionRate());
        return "index";
    }

    @PostMapping("/check/{routineId}")
    public String toggleCheck(@PathVariable Long routineId,
                              @RequestParam(defaultValue = "") String returnDate) {
        LocalDate date = returnDate.isBlank() ? LocalDate.now() : LocalDate.parse(returnDate);
        routineService.toggleCheck(routineId, date);
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
