package com.habit.tracker.service;

import com.habit.tracker.domain.Level;
import com.habit.tracker.domain.UserStats;
import com.habit.tracker.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 단일 사용자 XP / 레벨 통계 서비스
 *
 * UserStats 는 id=1L 싱글턴으로 관리된다.
 * DB 에 레코드가 없으면 최초 접근 시 자동 생성한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    /** id=1L 레코드를 가져오거나 없으면 신규 생성해 반환한다. */
    private UserStats getOrCreate() {
        return userStatsRepository.findById(1L)
                .orElseGet(() -> userStatsRepository.save(new UserStats()));
    }

    // ── XP 적립 ──────────────────────────────────────────────────────────────

    /**
     * XP 를 적립한다.
     * XP 는 차감되지 않는다 (음수 amount 도 기술적으로 허용하나 정책상 호출하지 않는다).
     */
    public void awardXp(int amount) {
        UserStats stats = getOrCreate();
        stats.setTotalXp(stats.getTotalXp() + amount);
        stats.setUpdatedAt(LocalDateTime.now());
    }

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public int getTotalXp() {
        return getOrCreate().getTotalXp();
    }

    @Transactional(readOnly = true)
    public Level getCurrentLevel() {
        return Level.fromXp(getOrCreate().getTotalXp());
    }

    /** 현재 레벨 시작 XP 기준 진행 XP */
    @Transactional(readOnly = true)
    public int getXpInCurrentLevel() {
        int totalXp = getOrCreate().getTotalXp();
        Level current = Level.fromXp(totalXp);
        return totalXp - current.getMinXp();
    }

    /**
     * 다음 레벨까지 남은 XP.
     * 만렙(LEGEND) 이면 0 을 반환한다.
     */
    @Transactional(readOnly = true)
    public int getXpToNextLevel() {
        int totalXp = getOrCreate().getTotalXp();
        Level current = Level.fromXp(totalXp);
        if (current == Level.LEGEND) {
            return 0;
        }
        return current.nextLevel().getMinXp() - totalXp;
    }

    /**
     * 현재 레벨 구간 내 진행률 (0~100).
     * 만렙이면 100 을 반환한다.
     */
    @Transactional(readOnly = true)
    public int getXpProgressPercent() {
        int totalXp = getOrCreate().getTotalXp();
        Level current = Level.fromXp(totalXp);
        if (current == Level.LEGEND) {
            return 100;
        }
        int levelRange = current.nextLevel().getMinXp() - current.getMinXp();
        int xpInLevel = totalXp - current.getMinXp();
        return (int) Math.round(xpInLevel * 100.0 / levelRange);
    }

    /**
     * 현재 레벨 구간 크기 (다음 레벨 minXp - 현재 레벨 minXp).
     * 디자인 스펙의 "80 / 200 XP" 에서 분모에 해당하는 값.
     * 만렙(LEGEND) 이면 0 을 반환한다.
     */
    @Transactional(readOnly = true)
    public int getXpLevelRange() {
        Level current = Level.fromXp(getOrCreate().getTotalXp());
        if (current == Level.LEGEND) return 0;
        return current.nextLevel().getMinXp() - current.getMinXp();
    }

    // ── 오늘 보너스 중복 방지 ─────────────────────────────────────────────────

    /** 오늘 전체 완료 보너스를 이미 받았는지 확인한다. */
    @Transactional(readOnly = true)
    public boolean isTodayBonusAwarded() {
        LocalDate lastBonus = getOrCreate().getLastBonusDate();
        return LocalDate.now().equals(lastBonus);
    }

    /** 오늘 전체 완료 보너스를 받은 것으로 기록한다. */
    public void markTodayBonusAwarded() {
        UserStats stats = getOrCreate();
        stats.setLastBonusDate(LocalDate.now());
    }
}
