package com.habit.tracker.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class RoutineForm {

    @NotBlank(message = "루틴 이름을 입력해주세요.")
    @Size(max = 100, message = "100자 이내로 입력해주세요.")
    private String name;

    @Size(max = 500, message = "500자 이내로 입력해주세요.")
    private String description;

    // 선택된 요일 목록 (예: ["MON", "WED", "FRI"])
    private List<String> days = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    public String getDaysOfWeek() {
        if (days == null || days.isEmpty()) return "MON,TUE,WED,THU,FRI,SAT,SUN";
        return String.join(",", days);
    }
}
