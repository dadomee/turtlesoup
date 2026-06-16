package com.turtlesoup.history;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "play_history")
public class PlayHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long puzzleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameMode mode;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private boolean solved;

    @Column(nullable = false)
    private Instant createdAt;

    protected PlayHistory() {
    }

    public PlayHistory(Long puzzleId, GameMode mode, String nickname, int questionCount, boolean solved) {
        this.puzzleId = puzzleId;
        this.mode = mode;
        this.nickname = nickname;
        this.questionCount = questionCount;
        this.solved = solved;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPuzzleId() { return puzzleId; }
    public GameMode getMode() { return mode; }
    public String getNickname() { return nickname; }
    public int getQuestionCount() { return questionCount; }
    public boolean isSolved() { return solved; }
    public Instant getCreatedAt() { return createdAt; }
}
