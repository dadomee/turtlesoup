package com.turtlesoup.room;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String code) {
        super("방을 찾을 수 없습니다: " + code);
    }
}
