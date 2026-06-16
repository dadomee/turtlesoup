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

    @Column(length = 1000) private String hint1;
    @Column(length = 1000) private String hint2;
    @Column(length = 1000) private String hint3;

    protected Puzzle() {
    }

    public Puzzle(String title, String scenario, String solution, Difficulty difficulty, String tags) {
        this.title = title;
        this.scenario = scenario;
        this.solution = solution;
        this.difficulty = difficulty;
        this.tags = tags;
    }

    public Puzzle(String title, String scenario, String solution, Difficulty difficulty, String tags,
                  String hint1, String hint2, String hint3) {
        this(title, scenario, solution, difficulty, tags);
        this.hint1 = hint1;
        this.hint2 = hint2;
        this.hint3 = hint3;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getScenario() { return scenario; }
    public String getSolution() { return solution; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getTags() { return tags; }
    public String getHint1() { return hint1; }
    public String getHint2() { return hint2; }
    public String getHint3() { return hint3; }
    public String getHint(int n) {
        return switch (n) { case 1 -> hint1; case 2 -> hint2; case 3 -> hint3; default -> null; };
    }
}
