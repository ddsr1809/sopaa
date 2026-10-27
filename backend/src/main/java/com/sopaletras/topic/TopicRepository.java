package com.sopaletras.topic;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findBySlugAndActiveTrue(String slug);

    List<Topic> findByActiveTrueOrderByNameAsc();

    @Query(value = "select word from topic_words where topic_id = :topicId order by id", nativeQuery = true)
    List<String> findWords(@Param("topicId") long topicId);

    @Query(value = "select topic_id as topicId, count(*) as total from topic_words group by topic_id", nativeQuery = true)
    List<TopicCount> countWords();

    /** Partidas por tema desde una fecha: para ordenar por "más jugados de la semana". */
    @Query(value = """
            select p.topic_id as topicId, count(*) as total
            from play_sessions s join puzzles p on p.id = s.puzzle_id
            where s.started_at >= :since and p.topic_id is not null
            group by p.topic_id
            """, nativeQuery = true)
    List<TopicCount> countPlaysSince(@Param("since") Instant since);

    @Query(value = "select topic_id from topic_favorites where user_id = :userId", nativeQuery = true)
    List<Long> findFavoriteIds(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            insert into topic_favorites (user_id, topic_id) values (:userId, :topicId)
            on conflict do nothing
            """, nativeQuery = true)
    void addFavorite(@Param("userId") UUID userId, @Param("topicId") long topicId);

    @Modifying
    @Query(value = "delete from topic_favorites where user_id = :userId and topic_id = :topicId", nativeQuery = true)
    void removeFavorite(@Param("userId") UUID userId, @Param("topicId") long topicId);

    interface TopicCount {
        Long getTopicId();

        Long getTotal();
    }
}
