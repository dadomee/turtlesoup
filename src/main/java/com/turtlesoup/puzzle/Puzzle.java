package com.turtlesoup.puzzle;

import jakarta.persistence.*;

@Entity
@Table(name = "puzzle")
public class Puzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String scenario;

    @Column(nullable = false, length = 2000)
    private String solution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(length = 200)
    private String tags; // 쉼표 구분

    protected Puzzle() {
    }

    public Puzzle(String title, String scenario, String solution, Difficulty difficulty, String tags) {
        this.title = title;
        this.scenario = scenario;
        this.solution = solution;
        this.difficulty = difficulty;
        this.tags = tags;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getScenario() { return scenario; }
    public String getSolution() { return solution; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getTags() { return tags; }
}
