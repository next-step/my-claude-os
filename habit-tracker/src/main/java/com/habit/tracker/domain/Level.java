package com.habit.tracker.domain;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 사용자 레벨 열거형
 *
 * 누적 XP 기준 7단계. fromXp(totalXp) 로 현재 레벨을 조회한다.
 * POLICY.md §3-1 레벨 구간 수치와 동일.
 */
public enum Level {

    SPROUT    (1, "새싹",   "🌱",      0),
    SAPLING   (2, "묘목",   "🌿",    200),
    TREE      (3, "나무",   "🌳",    600),
    FOREST    (4, "숲",     "🌲",   1500),
    GARDENER  (5, "정원사", "👨‍🌾",  3500),
    NATURALIST(6, "자연인", "🏕️",   7000),
    LEGEND    (7, "전설",   "⚡",  15000);

    private final int number;
    private final String name;
    private final String emoji;
    private final int minXp;

    Level(int number, String name, String emoji, int minXp) {
        this.number = number;
        this.name = name;
        this.emoji = emoji;
        this.minXp = minXp;
    }

    public int getNumber() { return number; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public int getMinXp() { return minXp; }

    /**
     * 누적 XP 를 받아 현재 레벨을 반환한다.
     * minXp 이하의 레벨 중 가장 높은 것을 선택 (내림차순 탐색).
     */
    public static Level fromXp(int totalXp) {
        return Arrays.stream(values())
                .filter(l -> totalXp >= l.minXp)
                .max(Comparator.comparingInt(Level::getMinXp))
                .orElse(SPROUT);
    }

    /**
     * 다음 레벨을 반환한다.
     * LEGEND(만렙) 이면 자기 자신을 반환한다.
     */
    public Level nextLevel() {
        Level[] levels = values();
        int nextOrdinal = this.ordinal() + 1;
        return nextOrdinal < levels.length ? levels[nextOrdinal] : this;
    }
}
