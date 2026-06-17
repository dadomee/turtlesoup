package com.turtlesoup.room;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LEN = 4;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public Room create(String hostName) {
        String code = newUniqueCode();
        Room room = new Room(code, hostName);
        rooms.put(code, room);
        return room;
    }

    public Room get(String code) {
        Room room = rooms.get(code == null ? "" : code.toUpperCase());
        if (room == null) throw new RoomNotFoundException(code);
        return room;
    }

    public Optional<Room> find(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(rooms.get(code.toUpperCase()));
    }

    public void join(String code, String nickname) { get(code).addParticipant(nickname); }
    public void leave(String code, String nickname) { find(code).ifPresent(r -> r.removeParticipant(nickname)); }
    public void remove(String code) { if (code != null) rooms.remove(code.toUpperCase()); }

    private String newUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LEN);
            for (int i = 0; i < CODE_LEN; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (rooms.containsKey(code));
        return code;
    }
}
