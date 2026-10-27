package com.sopaletras.puzzle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sopaletras.puzzle.engine.Difficulty;
import com.sopaletras.puzzle.engine.GeneratedGrid;
import com.sopaletras.puzzle.engine.PlacedWord;
import com.sopaletras.topic.Topic;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "puzzles")
public class Puzzle implements org.springframework.data.domain.Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PuzzleKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(name = "grid_rows", nullable = false)
    private int rows;

    @Column(name = "grid_cols", nullable = false)
    private int cols;

    @Column(nullable = false)
    private long seed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> grid;

    private String title;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_title")
    private String sourceTitle;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "daily_date")
    private LocalDate dailyDate;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "puzzle", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PuzzleWord> words = new ArrayList<>();

    protected Puzzle() {
    }

    public Puzzle(PuzzleKind kind, Topic topic, Difficulty difficulty, long seed, GeneratedGrid g, Instant now) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.topic = topic;
        this.difficulty = difficulty;
        this.seed = seed;
        this.rows = g.rows();
        this.cols = g.cols();
        this.grid = new ArrayList<>(g.grid());
        this.createdAt = now;
        int i = 0;
        for (PlacedWord pw : g.words()) {
            words.add(new PuzzleWord(this, i++, pw));
        }
    }

    public List<PlacedWord> placedWords() {
        return words.stream().map(PuzzleWord::toPlaced).toList();
    }

    public PuzzleKind getKind() {
        return kind;
    }

    public Topic getTopic() {
        return topic;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public long getSeed() {
        return seed;
    }

    public List<String> getGrid() {
        return grid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSource(String type, String title, String url) {
        this.sourceType = type;
        this.sourceTitle = title;
        this.sourceUrl = url;
    }

    public LocalDate getDailyDate() {
        return dailyDate;
    }

    public void setDailyDate(LocalDate dailyDate) {
        this.dailyDate = dailyDate;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PuzzleWord> getWords() {
        return words;
    }

    @jakarta.persistence.Transient
    private boolean isNew = true;

    @jakarta.persistence.PostLoad
    @jakarta.persistence.PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }
}
