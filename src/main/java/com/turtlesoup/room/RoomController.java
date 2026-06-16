package com.turtlesoup.room;

import com.turtlesoup.puzzle.Puzzle;
import com.turtlesoup.puzzle.PuzzleNotFoundException;
import com.turtlesoup.puzzle.PuzzleRepository;
import com.turtlesoup.room.dto.CreateRoomRequest;
import com.turtlesoup.room.dto.CreateRoomResponse;
import com.turtlesoup.room.dto.RoomInfo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService rooms;
    private final PuzzleRepository puzzles;

    public RoomController(RoomService rooms, PuzzleRepository puzzles) {
        this.rooms = rooms;
        this.puzzles = puzzles;
    }

    @PostMapping
    public CreateRoomResponse create(@Valid @RequestBody CreateRoomRequest req) {
        String title, scenario, solution;
        if (req.puzzleId() != null) {
            Puzzle p = puzzles.findById(req.puzzleId())
                .orElseThrow(() -> new PuzzleNotFoundException(req.puzzleId()));
            title = p.getTitle();
            scenario = p.getScenario();
            solution = p.getSolution();
        } else {
            if (isBlank(req.scenario()) || isBlank(req.solution())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "직접 출제 시 상황과 정답을 입력해야 합니다.");
            }
            title = isBlank(req.title()) ? "직접 출제 문제" : req.title();
            scenario = req.scenario();
            solution = req.solution();
        }
        Room room = rooms.create(req.hostName(), title, scenario, solution);
        return new CreateRoomResponse(room.getCode(), room.getTitle(), room.getScenario(), room.getSolution());
    }

    @GetMapping("/{code}")
    public RoomInfo info(@PathVariable String code) {
        Room r = rooms.get(code);
        return new RoomInfo(r.getCode(), r.getHostName(), r.getTitle(), r.getScenario(),
            r.isEnded(), r.participants());
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({RoomNotFoundException.class, PuzzleNotFoundException.class})
    public Map<String, String> handleNotFound(RuntimeException ex) {
        return Map.of("error", ex.getMessage());
    }
}
