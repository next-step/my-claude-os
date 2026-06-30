package com.habit.tracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "routines")
@Getter @Setter
@NoArgsConstructor
public class Routine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * 반복 요일 — "MON,TUE,WED,THU,FRI,SAT,SUN" 형식으로 저장
     * DayOfWeek.name()의 앞 3글자를 사용 (MONDAY → MON)
     */
    @Column(name = "days_of_week", nullable = false, length = 50)
    private String daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN";

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Routine(String name, String description, String daysOfWeek) {
        this.name = name;
        this.description = description;
        this.daysOfWeek = daysOfWeek;
    }

    public List<String> getDayList() {
        return Arrays.asList(daysOfWeek.split(","));
    }

    public boolean isScheduledFor(DayOfWeek dow) {
        String key = dow.name().substring(0, 3); // "MONDAY" → "MON"
        return getDayList().contains(key);
    }
}
