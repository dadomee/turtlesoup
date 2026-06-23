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
    private volatile boolean ended;
    private volatile int hintsUsed;
    private boolean aiHosted;
    private final Set<String> participants = Collections.synchronizedSet(new LinkedHashSet<>());

    public Room(String code, String hostName) {
        this.code = code;
        this.hostName = hostName;
    }

    public void setPuzzle(String title, String scenario, String solution) {
        this.title = title;
        this.scenario = scenario;
        this.solution = solution;
    }

    public boolean hasPuzzle() { return scenario != null; }

    public String getCode() { return code; }
    public String getHostName() { return hostName; }
    public String getTitle() { return title; }
    public String getScenario() { return scenario; }
    public String getSolution() { return solution; }
    public boolean isEnded() { return ended; }
    public void end() { this.ended = true; }
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
