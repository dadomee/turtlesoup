package com.turtlesoup.room;

import com.turtlesoup.room.dto.CreateRoomRequest;
import com.turtlesoup.room.dto.CreateRoomResponse;
import com.turtlesoup.room.dto.RoomInfo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    public CreateRoomResponse create(@Valid @RequestBody CreateRoomRequest req) {
        return new CreateRoomResponse(rooms.create(req.hostName()).getCode());
    }

    @GetMapping("/{code}")
    public RoomInfo info(@PathVariable String code) {
        Room r = rooms.get(code);
        return new RoomInfo(r.getCode(), r.getHostName(), r.getTitle(), r.getScenario(),
            r.isEnded(), r.participants());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(RoomNotFoundException.class)
    public Map<String, String> handleNotFound(RoomNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }
}
