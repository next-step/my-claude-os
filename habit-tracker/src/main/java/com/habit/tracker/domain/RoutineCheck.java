package com.habit.tracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "routine_checks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"routine_id", "check_date"}))
@Getter @Setter
@NoArgsConstructor
public class RoutineCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    /**
     * XP 중복 지급 방지 플래그 (POLICY.md §3-1)
     * 동일 루틴+날짜 조합에서 최초 완료 1회만 XP 를 지급한다.
     * 체크 해제 후 재체크 시에도 이 값이 true 이면 추가 지급하지 않는다.
     */
    @Column(name = "xp_awarded", nullable = false)
    private boolean xpAwarded = false;

    public static RoutineCheck of(Routine routine, LocalDate date, boolean completed) {
        RoutineCheck check = new RoutineCheck();
        check.routine = routine;
        check.checkDate = date;
        check.completed = completed;
        if (completed) check.checkedAt = LocalDateTime.now();
        return check;
    }
}
