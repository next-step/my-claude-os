package com.habit.tracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 단일 사용자 통계 엔티티 (싱글턴, id=1L 고정)
 *
 * totalXp : 누적 XP (XP 는 차감되지 않는다)
 * lastBonusDate : 하루 전체 완료 보너스 중복 방지용 날짜
 * updatedAt : XP 변경 시 갱신
 */
@Entity
@Table(name = "user_stats")
@Getter
@Setter
@NoArgsConstructor
public class UserStats {

    @Id
    private Long id = 1L;

    @Column(name = "total_xp", nullable = false)
    private int totalXp = 0;

    @Column(name = "last_bonus_date")
    private LocalDate lastBonusDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
