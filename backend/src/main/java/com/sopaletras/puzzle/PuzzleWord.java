package com.sopaletras.puzzle;

import com.sopaletras.puzzle.engine.Direction;
import com.sopaletras.puzzle.engine.PlacedWord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "puzzle_words")
public class PuzzleWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "puzzle_id")
    private Puzzle puzzle;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false)
    private String display;

    @Column(name = "start_row", nullable = false)
    private int startRow;

    @Column(name = "start_col", nullable = false)
    private int startCol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    protected PuzzleWord() {
    }

    PuzzleWord(Puzzle puzzle, int position, PlacedWord pw) {
        this.puzzle = puzzle;
        this.position = position;
        this.word = pw.word();
        this.display = pw.display();
        this.startRow = pw.row();
        this.startCol = pw.col();
        this.direction = pw.direction();
    }

    public PlacedWord toPlaced() {
        return new PlacedWord(word, display, startRow, startCol, direction);
    }

    public String getWord() {
        return word;
    }
}
