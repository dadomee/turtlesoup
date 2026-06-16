package com.turtlesoup.room.dto;

import java.util.List;

public record RoomInfo(String code, String hostName, String title, String scenario,
                       boolean ended, List<String> participants) {}
