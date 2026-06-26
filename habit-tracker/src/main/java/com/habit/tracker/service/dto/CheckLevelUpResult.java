package com.habit.tracker.service.dto;

import com.habit.tracker.domain.Level;

/**
 * toggleCheck 실행 결과 DTO
 *
 * leveledUp : XP 지급 후 레벨이 상승했으면 true
 * prevLevel : 지급 전 레벨 (레벨업 없으면 newLevel 과 동일)
 * newLevel  : 지급 후 레벨
 */
public class CheckLevelUpResult {

    private static final CheckLevelUpResult NO_LEVEL_UP = new CheckLevelUpResult(false, null, null);

    private final boolean leveledUp;
    private final Level prevLevel;
    private final Level newLevel;

    private CheckLevelUpResult(boolean leveledUp, Level prevLevel, Level newLevel) {
        this.leveledUp = leveledUp;
        this.prevLevel = prevLevel;
        this.newLevel = newLevel;
    }

    public static CheckLevelUpResult noChange() {
        return NO_LEVEL_UP;
    }

    public static CheckLevelUpResult levelUp(Level prevLevel, Level newLevel) {
        return new CheckLevelUpResult(true, prevLevel, newLevel);
    }

    public boolean isLeveledUp() { return leveledUp; }
    public Level getPrevLevel() { return prevLevel; }
    public Level getNewLevel() { return newLevel; }
}
