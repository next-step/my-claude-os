package com.habit.tracker.web;

import com.habit.tracker.service.RoutineService;
import com.habit.tracker.service.dto.RoutineForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/routines")
@RequiredArgsConstructor
public class RoutineController {

    private final RoutineService routineService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("routines", routineService.findAllActive());
        model.addAttribute("form", new RoutineForm());
        return "routines/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") RoutineForm form,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            model.addAttribute("routines", routineService.findAllActive());
            return "routines/list";
        }
        routineService.createRoutine(form);
        return "redirect:/routines";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        routineService.deactivateRoutine(id);
        return "redirect:/routines";
    }
}
