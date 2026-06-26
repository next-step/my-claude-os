package com.habit.tracker.repository;

import com.habit.tracker.domain.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatsRepository extends JpaRepository<UserStats, Long> {
}
