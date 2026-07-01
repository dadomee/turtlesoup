package com.turtlesoup.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Room {

    private final String code;
    private final String hostName;
    private String title;
    private String scenario;
    private String solution;
    private String hint1, hint2, hint3;   // AI 방: 사전작성 힌트(문제-보관함과 동일) — 힌트 요청 시 LLM 없이 사용
    private volatile boolean ended;
    private volatile int hintsUsed;
    private boolean aiHosted;
    private final Set<String> participants = Collections.synchronizedSet(new LinkedHashSet<>());

    public Room(String code, String hostName) {
        this.code = code;
        this.hostName = hostName;
    }

    public void setPuzzle(String title, String scenario, String solution) {
        setPuzzle(title, scenario, solution, null, null, null);
    }

    // AI 방: 사전작성 힌트까지 함께 저장 → 힌트 요청 시 LLM 없이 그대로 내려준다
    public void setPuzzle(String title, String scenario, String solution, String hint1, String hint2, String hint3) {
        this.title = title;
        this.scenario = scenario;
        this.solution = solution;
        this.hint1 = hint1;
        this.hint2 = hint2;
        this.hint3 = hint3;
    }

    public String getHint(int n) {
        return switch (n) { case 1 -> hint1; case 2 -> hint2; case 3 -> hint3; default -> null; };
    }

    public boolean hasPuzzle() { return scenario != null; }

    public String getCode() { return code; }
    public String getHostName() { return hostName; }
    public String getTitle() { return title; }
    public String getScenario() { return scenario; }
    public String getSolution() { return solution; }
    public boolean isEnded() { return ended; }
    public void end() { this.ended = true; }
    public void reset() {  // 새 게임: 문제·힌트·종료상태 초기화
        this.title = null;
        this.scenario = null;
        this.solution = null;
        this.hint1 = null; this.hint2 = null; this.hint3 = null;
        this.ended = false;
        this.hintsUsed = 0;
    }
    public boolean isHost(String name) { return hostName.equals(name); }

    public boolean isAiHosted() { return aiHosted; }
    public void setAiHosted(boolean v) { this.aiHosted = v; }

    public int getHintsUsed() { return hintsUsed; }
    public boolean canHint() { return hintsUsed < 3; }
    public int useHint() { return ++hintsUsed; }

    public void addParticipant(String name) { participants.add(name); }
    public void removeParticipant(String name) { participants.remove(name); }
    public List<String> participants() {
        synchronized (participants) { return new ArrayList<>(participants); }
    }
}
