package com.habit.tracker.repository;

import com.habit.tracker.domain.RoutineCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoutineCheckRepository extends JpaRepository<RoutineCheck, Long> {

    Optional<RoutineCheck> findByRoutineIdAndCheckDate(Long routineId, LocalDate date);

    List<RoutineCheck> findByCheckDateBetweenOrderByCheckDateAsc(LocalDate start, LocalDate end);

    List<RoutineCheck> findByRoutineIdAndCheckDateBetween(Long routineId, LocalDate start, LocalDate end);

    @Query("SELECT rc FROM RoutineCheck rc JOIN FETCH rc.routine WHERE rc.checkDate = :date")
    List<RoutineCheck> findByCheckDateWithRoutine(@Param("date") LocalDate date);
}
