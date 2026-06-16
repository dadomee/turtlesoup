package com.turtlesoup.room;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class RoomSocketHandler extends TextWebSocketHandler {

    private final RoomService rooms;
    private final ObjectMapper mapper = new ObjectMapper();
    // code -> 세션 집합
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public RoomSocketHandler(RoomService rooms) {
        this.rooms = rooms;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String code = codeFrom(session.getUri());
        session.getAttributes().put("code", code);
        sessions.computeIfAbsent(code, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
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
                    "text", nick + " 님이 입장했습니다.",
                    "participants", room.participants()));
            }
            case "ask" -> broadcast(code, Map.of("type", "question",
                "nickname", str(msg.get("nickname")), "text", str(msg.get("text"))));
            case "answer" -> {
                String nick = str(msg.get("nickname"));
                if (!room.isHost(nick) || room.isEnded()) return;
                String verdict = str(msg.get("verdict"));
                broadcast(code, Map.of("type", "answer", "verdict", verdict));
                if ("CORRECT".equals(verdict)) {
                    room.end();
                    broadcast(code, Map.of("type", "reveal",
                        "solution", room.getSolution(), "ended", true));
                }
            }
            case "reveal" -> {
                String nick = str(msg.get("nickname"));
                if (!room.isHost(nick) || room.isEnded()) return;
                room.end();
                broadcast(code, Map.of("type", "reveal",
                    "solution", room.getSolution(), "ended", true));
            }
            case "hint" -> {
                String nick = str(msg.get("nickname"));
                if (!room.isHost(nick) || room.isEnded() || !room.canHint()) return;
                String text = str(msg.get("text"));
                if (text.isBlank()) return;
                int n = room.useHint();
                broadcast(code, Map.of("type", "hint", "text", text, "count", n, "max", 3));
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
                    "text", nick + " 님이 퇴장했습니다.",
                    "participants", room.participants()));
            }
        });
    }

    private void broadcast(String code, Map<String, Object> payload) {
        Set<WebSocketSession> set = sessions.get(code);
        if (set == null) return;
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                synchronized (s) {
                    try {
                        s.sendMessage(new TextMessage(json));
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private String codeFrom(URI uri) {
        String path = uri.getPath();              // /ws/room/AB12
        return path.substring(path.lastIndexOf('/') + 1).toUpperCase();
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
}
