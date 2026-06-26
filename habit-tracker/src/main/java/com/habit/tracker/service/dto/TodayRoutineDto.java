package com.habit.tracker.service.dto;

import com.habit.tracker.domain.Routine;
import lombok.Getter;

@Getter
public class TodayRoutineDto {
    private final Long id;
    private final String name;
    private final String description;
    private final boolean completed;

    public TodayRoutineDto(Routine routine, boolean completed) {
        this.id = routine.getId();
        this.name = routine.getName();
        this.description = routine.getDescription();
        this.completed = completed;
    }
}
