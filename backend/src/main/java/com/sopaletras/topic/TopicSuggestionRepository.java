package com.sopaletras.topic;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicSuggestionRepository extends JpaRepository<TopicSuggestion, Long> {

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);
}
