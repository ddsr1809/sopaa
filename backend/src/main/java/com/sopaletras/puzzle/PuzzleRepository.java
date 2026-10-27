package com.sopaletras.puzzle;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sopaletras.puzzle.engine.Difficulty;

public interface PuzzleRepository extends JpaRepository<Puzzle, UUID> {

    @EntityGraph(attributePaths = {"words", "topic"})
    Optional<Puzzle> findWithWordsById(UUID id);

    @EntityGraph(attributePaths = {"words", "topic"})
    Optional<Puzzle> findByKindAndDailyDateAndDifficulty(PuzzleKind kind, LocalDate dailyDate, Difficulty difficulty);
}
