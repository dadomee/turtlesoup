package com.turtlesoup.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turtlesoup.ai.AiJudgeService;
import com.turtlesoup.ai.Verdict;
import com.turtlesoup.puzzle.Puzzle;
import com.turtlesoup.puzzle.PuzzleRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class RoomSocketHandler extends TextWebSocketHandler {

    private final RoomService rooms;
    private final PuzzleRepository puzzles;
    private final AiJudgeService ai;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ExecutorService aiPool = Executors.newCachedThreadPool();

    public RoomSocketHandler(RoomService rooms, PuzzleRepository puzzles, AiJudgeService ai) {
        this.rooms = rooms;
        this.puzzles = puzzles;
        this.ai = ai;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String code = codeFrom(session.getUri());
        session.getAttributes().put("code", code);
        sessions.computeIfAbsent(code, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        String type = str(msg.get("type"));
        String code = (String) session.getAttributes().get("code");
        Room room = rooms.find(code).orElse(null);
        if (room == null) return;

        switch (type) {
            case "join" -> {
                String nick = str(msg.get("nickname"));
                session.getAttributes().put("nickname", nick);
                room.addParticipant(nick);
                broadcast(code, Map.of("type", "system",
                    "text", nick + " 님이 입장했습니다.", "participants", room.participants()));
                if (room.hasPuzzle()) {
                    sendTo(session, Map.of("type", "puzzle",
                        "title", room.getTitle(), "scenario", room.getScenario()));
                    if (room.isHost(nick) && !room.isAiHosted()) {
                        sendTo(session, Map.of("type", "solution", "solution", room.getSolution()));
                    }
                }
            }
            case "setPuzzle" -> {
                String nick = str(msg.get("nickname"));
                if (!room.isHost(nick) || room.hasPuzzle()) return;
                String title;
                String scenario;
                String solution;
                Object pid = msg.get("puzzleId");
                if (pid != null) {
                    long id = pid instanceof Number ? ((Number) pid).longValue() : Long.parseLong(pid.toString());
                    Puzzle p = puzzles.findById(id).orElse(null);
                    if (p == null) return;
                    title = p.getTitle();
                    scenario = p.getScenario();
                    solution = p.getSolution();
                } else {
                    scenario = str(msg.get("scenario"));
                    solution = str(msg.get("solution"));
                    if (scenario.isBlank() || solution.isBlank()) return;
                    String t = str(msg.get("title"));
                    title = t.isBlank() ? "직접 출제 문제" : t;
                }
                room.setPuzzle(title, scenario, solution);
                broadcast(code, Map.of("type", "puzzle", "title", title, "scenario", scenario));
                sendToHost(room, code, Map.of("type", "solution", "solution", solution));
            }
            case "chat" -> {
                String text = str(msg.get("text"));
                if (text.isBlank()) return;
                broadcast(code, Map.of("type", "chat",
                    "nickname", str(msg.get("nickname")), "text", text));
            }
            case "ask" -> {
                if (!room.hasPuzzle()) return;
                broadcast(code, Map.of("type", "question",
                    "nickname", str(msg.get("nickname")), "text", str(msg.get("text"))));
                if (room.isAiHosted() && !room.isEnded()) {
                    final String scenario = room.getScenario();
                    final String solution = room.getSolution();
                    final String q = str(msg.get("text"));
                    aiPool.submit(() -> {
                        Verdict v = ai.judge(scenario, solution, q);
                        broadcast(code, Map.of("type", "answer", "verdict", v.name()));
                        if (v == Verdict.CORRECT) {
                            room.end();
                            broadcast(code, Map.of("type", "system", "text", "🎉 정답입니다! 해설을 공개해요."));
                            broadcast(code, Map.of("type", "reveal", "solution", solution, "ended", true));
                        }
                    });
                }
            }
            case "answer" -> {
                String nick = str(msg.get("nickname"));
                if (room.isAiHosted() || !room.isHost(nick) || room.isEnded() || !room.hasPuzzle()) return;
                String verdict = str(msg.get("verdict"));
                broadcast(code, Map.of("type", "answer", "verdict", verdict));
                if ("CORRECT".equals(verdict)) {
                    room.end();
                    broadcast(code, Map.of("type", "system", "text", "🎉 정답입니다! 해설을 공개해요."));
                    broadcast(code, Map.of("type", "reveal", "solution", room.getSolution(), "ended", true));
                }
            }
            case "reveal" -> {
                if (room.isEnded() || !room.hasPuzzle()) return;
                String nick = str(msg.get("nickname"));
                if (!room.isAiHosted() && !room.isHost(nick)) return; // 사람 방은 출제자만, AI 방은 누구나
                room.end();
                broadcast(code, Map.of("type", "system",
                    "text", (nick.isBlank() ? "누군가" : nick) + "님이 정답을 공개했어요 🏳️"));
                broadcast(code, Map.of("type", "reveal", "solution", room.getSolution(), "ended", true));
            }
            case "hint" -> {
                if (room.isEnded() || !room.hasPuzzle() || !room.canHint()) return;
                if (room.isAiHosted()) {
                    String nick = str(msg.get("nickname"));
                    int n = room.useHint();
                    broadcast(code, Map.of("type", "system",
                        "text", (nick.isBlank() ? "누군가" : nick) + "님이 힌트를 요청했어요 💡 (" + n + "/3)"));
                    final String scenario = room.getScenario();
                    final String solution = room.getSolution();
                    aiPool.submit(() -> {
                        String text = ai.hint(scenario, solution, n);
                        broadcast(code, Map.of("type", "hint", "text", text, "count", n, "max", 3));
                    });
                } else {
                    String nick = str(msg.get("nickname"));
                    if (!room.isHost(nick)) return;
                    String text = str(msg.get("text"));
                    if (text.isBlank()) return;
                    int n = room.useHint();
                    broadcast(code, Map.of("type", "hint", "text", text, "count", n, "max", 3));
                }
            }
            case "newGame" -> {
                String nick = str(msg.get("nickname"));
                if (room.isAiHosted()) {
                    String prev = room.getTitle();
                    Puzzle p = rooms.randomPuzzle().orElse(null);
                    for (int i = 0; i < 5 && p != null && p.getTitle().equals(prev); i++) {
                        p = rooms.randomPuzzle().orElse(p);
                    }
                    if (p == null) return;
                    room.reset();
                    room.setPuzzle(p.getTitle(), p.getScenario(), p.getSolution());
                    broadcast(code, Map.of("type", "newgame"));
                    broadcast(code, Map.of("type", "system", "text", "🔄 새 게임! 새 문제가 나왔어요."));
                    broadcast(code, Map.of("type", "puzzle", "title", p.getTitle(), "scenario", p.getScenario()));
                } else {
                    if (!room.isHost(nick)) return;
                    room.reset();
                    broadcast(code, Map.of("type", "newgame"));
                    broadcast(code, Map.of("type", "system", "text", "🔄 출제자가 새 문제를 준비합니다."));
                }
            }
            default -> { }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String code = (String) session.getAttributes().get("code");
        String nick = (String) session.getAttributes().get("nickname");
        Set<WebSocketSession> set = sessions.get(code);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(code);
                rooms.remove(code);
                return;
            }
        }
        rooms.find(code).ifPresent(room -> {
            if (nick != null) {
                room.removeParticipant(nick);
                broadcast(code, Map.of("type", "system",
                    "text", nick + " 님이 퇴장했습니다.", "participants", room.participants()));
            }
        });
    }

    private void broadcast(String code, Map<String, Object> payload) {
        Set<WebSocketSession> set = sessions.get(code);
        if (set == null) return;
        String json = toJson(payload);
        if (json == null) return;
        for (WebSocketSession s : set) sendRaw(s, json);
    }

    private void sendToHost(Room room, String code, Map<String, Object> payload) {
        Set<WebSocketSession> set = sessions.get(code);
        if (set == null) return;
        String json = toJson(payload);
        if (json == null) return;
        for (WebSocketSession s : set) {
            if (room.getHostName().equals(s.getAttributes().get("nickname"))) sendRaw(s, json);
        }
    }

    private void sendTo(WebSocketSession session, Map<String, Object> payload) {
        String json = toJson(payload);
        if (json != null) sendRaw(session, json);
    }

    private void sendRaw(WebSocketSession s, String json) {
        if (s.isOpen()) {
            synchronized (s) {
                try { s.sendMessage(new TextMessage(json)); } catch (IOException ignored) { }
            }
        }
    }

    private String toJson(Map<String, Object> payload) {
        try { return mapper.writeValueAsString(payload); } catch (Exception e) { return null; }
    }

    private String codeFrom(URI uri) {
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1).toUpperCase();
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
}
