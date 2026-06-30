package com.habit.tracker.repository;

import com.habit.tracker.domain.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findByActiveTrueOrderByCreatedAtAsc();
}
