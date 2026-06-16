package com.turtlesoup.room;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class Room {

    private final String code;
    private final String hostName;
    private final String title;
    private final String scenario;
    private final String solution;
    private volatile boolean ended;
    private final Set<String> participants = Collections.synchronizedSet(new LinkedHashSet<>());

    private volatile int hintsUsed;

    public Room(String code, String hostName, String title, String scenario, String solution) {
        this.code = code;
        this.hostName = hostName;
        this.title = title;
        this.scenario = scenario;
        this.solution = solution;
    }

    public String getCode() { return code; }
    public String getHostName() { return hostName; }
    public String getTitle() { return title; }
    public String getScenario() { return scenario; }
    public String getSolution() { return solution; }
    public boolean isEnded() { return ended; }
    public void end() { this.ended = true; }
    public boolean isHost(String name) { return hostName.equals(name); }

    public void addParticipant(String name) { participants.add(name); }
    public void removeParticipant(String name) { participants.remove(name); }
    public java.util.List<String> participants() {
        synchronized (participants) { return new java.util.ArrayList<>(participants); }
    }

    public int getHintsUsed() { return hintsUsed; }
    public boolean canHint() { return hintsUsed < 3; }
    public int useHint() { return ++hintsUsed; }
}
