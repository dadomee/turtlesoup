# 바다거북스프 — ③ 멀티플레이(팀-라운지) 구현 계획 (플랜 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사람 출제자가 방을 만들고(기존 문제 선택 또는 직접 출제), 참가자들이 방 코드로 입장해 실시간으로 질문하면 출제자가 예/아니오/상관없음/정답 4버튼으로 답하는 멀티플레이 모드.

**Architecture:** 순수 WebSocket(Spring `TextWebSocketHandler`, STOMP/SockJS 미사용 → 외부 JS 의존 0). 방은 메모리(`RoomService`, DB 미사용, 휘발성). 방 생성/조회는 REST, 실시간 메시지는 WebSocket. 정답(solution)은 출제자 본인(생성자)에게만 내려가고, 참가자에겐 "정답" 또는 "정답 공개" 시에만 공개.

**Tech Stack:** Spring Boot 3.5.15, `spring-boot-starter-websocket`, Jackson, 바닐라 JS(native WebSocket), JUnit 5

**전제:** 플랜 1·2 완료. `puzzle` 패키지(Puzzle/PuzzleRepository/PuzzleNotFoundException) 존재. 닉네임은 localStorage. 정적 셸(rail/sidebar/topbar) + theme.js/shell.js/boss.js 존재.

---

## File Structure

```
src/main/java/com/turtlesoup/room/
├─ Room.java                    방 상태(메모리 POJO)
├─ RoomService.java             방 생성/조회/입퇴장 + 코드 생성
├─ RoomNotFoundException.java
├─ WebSocketConfig.java         /ws/room/* 핸들러 등록
├─ RoomSocketHandler.java       실시간 메시지 라우팅(TextWebSocketHandler)
├─ RoomController.java          POST /api/rooms, GET /api/rooms/{code}
└─ dto/CreateRoomRequest.java, dto/CreateRoomResponse.java, dto/RoomInfo.java

src/main/resources/static/
├─ room.html                    팀-라운지(로비 + 방)
├─ js/room.js                   로비/방/WebSocket 클라이언트
└─ index.html, classic.html, ai.html  (# 팀-라운지 nav 활성화)

src/test/java/com/turtlesoup/room/
├─ RoomServiceTest.java
└─ RoomControllerTest.java
```

메시지 프로토콜(JSON):
- 클라 → 서버: `{type:"join", nickname}` · `{type:"ask", nickname, text}` · `{type:"answer", nickname, verdict}` · `{type:"reveal", nickname}`
- 서버 → 클라(브로드캐스트, `/topic` 대신 방의 전체 세션에 직접 전송): `{type:"system", text, participants}` · `{type:"question", nickname, text}` · `{type:"answer", verdict}` · `{type:"reveal", solution, ended:true}`
- verdict 값은 AI 모드와 동일: `YES|NO|IRRELEVANT|CORRECT`. CORRECT면 곧바로 reveal+종료.

---

### Task 1: WebSocket 의존성 + 설정 + 빈 핸들러 골격

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/com/turtlesoup/room/WebSocketConfig.java`
- Create: `src/main/java/com/turtlesoup/room/RoomSocketHandler.java` (골격)

- [ ] **Step 1: build.gradle에 websocket 스타터 추가**

`dependencies`에 한 줄 추가(다른 의존성은 그대로):
```groovy
	implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

- [ ] **Step 2: 빈 핸들러 골격 작성** (Task 4에서 채움)

`src/main/java/com/turtlesoup/room/RoomSocketHandler.java`:
```java
package com.turtlesoup.room;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextWebSocketHandler;

@Component
public class RoomSocketHandler extends TextWebSocketHandler {
}
```

- [ ] **Step 3: WebSocketConfig 작성**

`src/main/java/com/turtlesoup/room/WebSocketConfig.java`:
```java
package com.turtlesoup.room;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomSocketHandler handler;

    public WebSocketConfig(RoomSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/room/*").setAllowedOriginPatterns("*");
    }
}
```

- [ ] **Step 4: 컨텍스트 기동 확인**

Run: `./gradlew test`
Expected: 기존 테스트 전부 통과(@SpringBootTest 컨텍스트에 WebSocket 빈 로드). `BUILD SUCCESSFUL`.

- [ ] **Step 5: 커밋**
```bash
git add build.gradle src/main/java/com/turtlesoup/room/WebSocketConfig.java src/main/java/com/turtlesoup/room/RoomSocketHandler.java
git commit -m "feat: add Spring WebSocket endpoint /ws/room/* (skeleton)"
```

---

### Task 2: Room 모델 + RoomService (TDD)

**Files:**
- Create: `src/main/java/com/turtlesoup/room/Room.java`
- Create: `src/main/java/com/turtlesoup/room/RoomNotFoundException.java`
- Create: `src/main/java/com/turtlesoup/room/RoomService.java`
- Test: `src/test/java/com/turtlesoup/room/RoomServiceTest.java`

- [ ] **Step 1: Room 작성**

`src/main/java/com/turtlesoup/room/Room.java`:
```java
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
}
```

- [ ] **Step 2: 예외 작성**

`src/main/java/com/turtlesoup/room/RoomNotFoundException.java`:
```java
package com.turtlesoup.room;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String code) {
        super("방을 찾을 수 없습니다: " + code);
    }
}
```

- [ ] **Step 3: 실패하는 RoomService 테스트 작성**

`src/test/java/com/turtlesoup/room/RoomServiceTest.java`:
```java
package com.turtlesoup.room;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomServiceTest {

    RoomService service = new RoomService();

    @Test
    void createReturnsRoomWithCode() {
        Room room = service.create("호스트", "제목", "상황", "정답");
        assertThat(room.getCode()).hasSize(4);
        assertThat(room.getHostName()).isEqualTo("호스트");
        assertThat(room.getSolution()).isEqualTo("정답");
        assertThat(service.get(room.getCode())).isSameAs(room);
    }

    @Test
    void codesAreUnique() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            codes.add(service.create("h", "t", "s", "a").getCode());
        }
        assertThat(codes).hasSize(200);
    }

    @Test
    void getThrowsWhenMissing() {
        assertThatThrownBy(() -> service.get("ZZZZ"))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void tracksParticipantsAndRemovesEmptyRoom() {
        Room room = service.create("호스트", "t", "s", "a");
        String code = room.getCode();
        service.join(code, "철수");
        service.join(code, "영희");
        assertThat(service.get(code).participants()).containsExactly("철수", "영희");

        service.leave(code, "철수");
        assertThat(service.get(code).participants()).containsExactly("영희");
    }

    @Test
    void removeDeletesRoom() {
        Room room = service.create("호스트", "t", "s", "a");
        service.remove(room.getCode());
        assertThatThrownBy(() -> service.get(room.getCode()))
            .isInstanceOf(RoomNotFoundException.class);
    }
}
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew test --tests RoomServiceTest`
Expected: 컴파일 실패 — `RoomService` 없음.

- [ ] **Step 5: RoomService 작성**

`src/main/java/com/turtlesoup/room/RoomService.java`:
```java
package com.turtlesoup.room;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    // 헷갈리는 글자(O,0,I,1) 제외
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LEN = 4;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public Room create(String hostName, String title, String scenario, String solution) {
        String code = newUniqueCode();
        Room room = new Room(code, hostName, title, scenario, solution);
        rooms.put(code, room);
        return room;
    }

    public Room get(String code) {
        Room room = rooms.get(code == null ? "" : code.toUpperCase());
        if (room == null) {
            throw new RoomNotFoundException(code);
        }
        return room;
    }

    public Optional<Room> find(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(rooms.get(code.toUpperCase()));
    }

    public void join(String code, String nickname) {
        get(code).addParticipant(nickname);
    }

    public void leave(String code, String nickname) {
        find(code).ifPresent(r -> r.removeParticipant(nickname));
    }

    public void remove(String code) {
        if (code != null) rooms.remove(code.toUpperCase());
    }

    private String newUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LEN);
            for (int i = 0; i < CODE_LEN; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (rooms.containsKey(code));
        return code;
    }
}
```

- [ ] **Step 6: 테스트 통과 + 커밋**

Run: `./gradlew test --tests RoomServiceTest` → PASS.
```bash
git add src/main/java/com/turtlesoup/room/Room.java src/main/java/com/turtlesoup/room/RoomNotFoundException.java src/main/java/com/turtlesoup/room/RoomService.java src/test/java/com/turtlesoup/room/RoomServiceTest.java
git commit -m "feat: add Room model and in-memory RoomService"
```

---

### Task 3: RoomController REST (TDD)

**Files:**
- Create: `src/main/java/com/turtlesoup/room/dto/CreateRoomRequest.java`
- Create: `src/main/java/com/turtlesoup/room/dto/CreateRoomResponse.java`
- Create: `src/main/java/com/turtlesoup/room/dto/RoomInfo.java`
- Create: `src/main/java/com/turtlesoup/room/RoomController.java`
- Test: `src/test/java/com/turtlesoup/room/RoomControllerTest.java`

- [ ] **Step 1: DTO 작성**

`dto/CreateRoomRequest.java` (puzzleId가 있으면 기존 문제, 없으면 custom):
```java
package com.turtlesoup.room.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoomRequest(
    @NotBlank String hostName,
    Long puzzleId,
    String title,
    String scenario,
    String solution
) {}
```

`dto/CreateRoomResponse.java` (생성자=호스트에게만 반환 → solution 포함 OK):
```java
package com.turtlesoup.room.dto;

public record CreateRoomResponse(String code, String title, String scenario, String solution) {}
```

`dto/RoomInfo.java` (참가자용 공개 정보 → solution 없음):
```java
package com.turtlesoup.room.dto;

import java.util.List;

public record RoomInfo(String code, String hostName, String title, String scenario,
                       boolean ended, List<String> participants) {}
```

- [ ] **Step 2: 실패하는 컨트롤러 테스트 작성**

`src/test/java/com/turtlesoup/room/RoomControllerTest.java`:
```java
package com.turtlesoup.room;

import com.turtlesoup.puzzle.Difficulty;
import com.turtlesoup.puzzle.Puzzle;
import com.turtlesoup.puzzle.PuzzleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RoomService rooms;
    @MockitoBean PuzzleRepository puzzles;

    @Test
    void createCustomRoomReturnsCodeAndSolution() throws Exception {
        when(rooms.create(eq("호스트"), eq("내문제"), eq("상황"), eq("정답")))
            .thenReturn(new Room("AB12", "호스트", "내문제", "상황", "정답"));

        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"호스트\",\"title\":\"내문제\",\"scenario\":\"상황\",\"solution\":\"정답\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("AB12"))
            .andExpect(jsonPath("$.solution").value("정답"));
    }

    @Test
    void createFromExistingPuzzleLooksUpSolution() throws Exception {
        when(puzzles.findById(1L)).thenReturn(Optional.of(
            new Puzzle("바다거북 스프", "상황S", "정답S", Difficulty.HARD, "고전")));
        when(rooms.create(eq("호스트"), eq("바다거북 스프"), eq("상황S"), eq("정답S")))
            .thenReturn(new Room("CD34", "호스트", "바다거북 스프", "상황S", "정답S"));

        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"호스트\",\"puzzleId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CD34"))
            .andExpect(jsonPath("$.scenario").value("상황S"));
    }

    @Test
    void blankCustomRoomReturns400() throws Exception {
        // puzzleId 없고 scenario/solution도 비면 400
        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"호스트\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getRoomInfoOmitsSolution() throws Exception {
        Room room = new Room("EF56", "호스트", "제목", "상황", "비밀정답");
        room.addParticipant("철수");
        when(rooms.get("EF56")).thenReturn(room);

        mockMvc.perform(get("/api/rooms/EF56"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scenario").value("상황"))
            .andExpect(jsonPath("$.participants[0]").value("철수"))
            .andExpect(jsonPath("$.solution").doesNotExist());
    }

    @Test
    void missingRoomReturns404() throws Exception {
        when(rooms.get("ZZZZ")).thenThrow(new RoomNotFoundException("ZZZZ"));
        mockMvc.perform(get("/api/rooms/ZZZZ"))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests RoomControllerTest` → 컴파일 실패(RoomController 없음).

- [ ] **Step 4: RoomController 작성**

`src/main/java/com/turtlesoup/room/RoomController.java`:
```java
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
```

- [ ] **Step 5: 테스트 통과 + 커밋**

Run: `./gradlew test --tests RoomControllerTest` → PASS.
```bash
git add src/main/java/com/turtlesoup/room/dto/ src/main/java/com/turtlesoup/room/RoomController.java src/test/java/com/turtlesoup/room/RoomControllerTest.java
git commit -m "feat: add RoomController (create existing/custom room, get info without solution)"
```

---

### Task 4: RoomSocketHandler — 실시간 메시지 라우팅

**Files:**
- Modify: `src/main/java/com/turtlesoup/room/RoomSocketHandler.java`

실시간 WebSocket 로직은 단위 테스트가 어렵다(브라우저 2탭 수동 통합 테스트로 검증, Task 6). 순수 로직(RoomService)은 Task 2에서 검증됨. 컴파일 + 컨텍스트 로드 + 수동 테스트로 확인한다.

- [ ] **Step 1: 핸들러 구현**

`src/main/java/com/turtlesoup/room/RoomSocketHandler.java`:
```java
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
                if (!room.isHost(nick)) return;
                room.end();
                broadcast(code, Map.of("type", "reveal",
                    "solution", room.getSolution(), "ended", true));
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
```

- [ ] **Step 2: 컴파일 + 전체 테스트**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` (RoomService/Controller 테스트 포함).

- [ ] **Step 3: 커밋**
```bash
git add src/main/java/com/turtlesoup/room/RoomSocketHandler.java
git commit -m "feat: real-time room message routing over WebSocket"
```

---

### Task 5: 프론트 — 팀-라운지(로비 + 방) + nav 활성화

**Files:**
- Create: `src/main/resources/static/room.html`
- Create: `src/main/resources/static/js/room.js`
- Modify: `index.html`, `classic.html`, `ai.html` (# 팀-라운지 nav를 `/room.html` 링크로 활성화)

- [ ] **Step 1: 세 페이지의 nav 활성화**

각 파일에서 다음 줄을
```html
        <span class="nav-item disabled"># 팀-라운지</span>
```
다음으로 교체:
```html
        <a class="nav-item" href="/room.html"># 팀-라운지</a>
```
(room.html에서는 이 항목에 `class="nav-item active"`로 둔다 — Step 2 참고.)
또한 `index.html`의 모드 선택 카드에서 "③ 멀티플레이는 곧 추가됩니다" 안내 카드(opacity .6)를 클릭 가능한 카드로 바꾼다:
```html
            <div class="card clickable" onclick="location.href='/room.html'">
              <h3># 팀-라운지</h3>
              <p class="muted">방을 만들거나 코드로 입장해 실시간으로 함께 추리합니다.</p>
            </div>
```

- [ ] **Step 2: room.html 작성**

`src/main/resources/static/room.html`:
```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title># 팀-라운지</title>
  <link rel="stylesheet" href="/css/style.css">
  <script src="/js/theme.js"></script>
</head>
<body>
  <div class="app">
    <nav class="rail" aria-label="앱">
      <div class="rail-item active"><span class="ic">💬</span>채팅</div>
      <div class="rail-item"><span class="ic">👥</span>팀</div>
      <div class="rail-item"><span class="ic">📅</span>일정</div>
      <div class="rail-item"><span class="ic">📁</span>파일</div>
    </nav>
    <aside class="sidebar">
      <div class="ws-header">(주)성실상사</div>
      <div class="nav">
        <span class="nav-label">워크스페이스</span>
        <a class="nav-item" href="/"># 홈</a>
        <a class="nav-item" href="/classic.html"># 문제-보관함</a>
        <a class="nav-item" href="/ai.html">@ 추리비서 AI</a>
        <a class="nav-item active" href="/room.html"># 팀-라운지</a>
      </div>
      <div class="sidebar-foot"><span class="status-dot"></span> <span data-me-name>게스트</span> · 근무중</div>
    </aside>
    <div class="main">
      <header class="topbar">
        <span class="topbar-title"># 팀-라운지</span>
        <span class="topbar-search">메시지, 파일, 사람 검색</span>
        <span class="topbar-spacer"></span>
        <span class="avatar" data-me-avatar>?</span>
      </header>
      <div class="content">
        <div class="content-narrow">

          <div id="lobby-view">
            <h1># 팀-라운지</h1>
            <div class="card">
              <h3>방 만들기 (출제자)</h3>
              <p class="muted">문제를 골라 방을 열고, 친구에게 방 코드를 알려주세요.</p>
              <div style="margin:10px 0;">
                <label><input type="radio" name="src" value="existing" checked> 기존 문제에서 선택</label>
                <select id="puzzle-select" style="margin-left:8px;"></select>
              </div>
              <div style="margin:10px 0;">
                <label><input type="radio" name="src" value="custom"> 직접 출제</label>
                <div id="custom-fields" class="hidden" style="margin-top:8px;">
                  <input type="text" id="custom-title" placeholder="제목(선택)" style="margin-bottom:6px;">
                  <textarea id="custom-scenario" placeholder="문제 상황(참가자에게 보여줄 내용)" rows="3" style="width:100%;margin-bottom:6px;"></textarea>
                  <textarea id="custom-solution" placeholder="정답/해설(나만 봄)" rows="2" style="width:100%;"></textarea>
                </div>
              </div>
              <button id="create-btn">방 만들기</button>
            </div>
            <div class="card">
              <h3>방 입장 (참가자)</h3>
              <div style="display:flex; gap:8px; margin-top:8px;">
                <input type="text" id="join-code" placeholder="방 코드 (예: AB12)" maxlength="4" style="text-transform:uppercase;">
                <button id="join-btn" style="white-space:nowrap;">입장</button>
              </div>
            </div>
          </div>

          <div id="room-view" class="hidden">
            <p><button class="ghost" onclick="leaveRoom()">← 나가기</button>
               <span class="badge diff" id="room-code-badge"></span></p>
            <div class="card">
              <h2 id="room-title"></h2>
              <p id="room-scenario" style="line-height:1.7;"></p>
              <div id="host-solution" class="hidden" style="margin-top:8px; background:var(--info-bg); border-radius:8px; padding:10px 12px;">
                <b style="font-weight:500;">정답(나만 봄):</b> <span id="host-solution-text"></span>
              </div>
            </div>
            <div class="card">
              <div id="room-log" class="chat-log"></div>

              <div id="participant-composer" class="composer hidden">
                <input type="text" id="room-input" placeholder="질문을 입력하세요 (예/아니오로 답할 수 있게)">
                <button id="room-send">질문</button>
              </div>

              <div id="host-controls" class="hidden" style="margin-top:12px;">
                <p class="muted" style="margin-bottom:6px;">참가자 질문에 답하세요:</p>
                <div style="display:flex; gap:8px; flex-wrap:wrap;">
                  <button class="ans" data-v="YES">예</button>
                  <button class="ans ghost" data-v="NO">아니오</button>
                  <button class="ans ghost" data-v="IRRELEVANT">상관없음</button>
                  <button class="ans" data-v="CORRECT" style="background:var(--warn);">정답 처리</button>
                  <button class="ghost" id="reveal-btn">정답 공개(종료)</button>
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  </div>

  <script src="/js/nickname.js"></script>
  <script src="/js/shell.js"></script>
  <script src="/js/boss.js"></script>
  <script src="/js/room.js"></script>
</body>
</html>
```

- [ ] **Step 3: room.js 작성**

`src/main/resources/static/js/room.js`:
```javascript
const lobby = document.getElementById("lobby-view");
const roomView = document.getElementById("room-view");

let ws = null;
let isHost = false;
let myCode = null;

const VERDICT = { YES: "예", NO: "아니오", IRRELEVANT: "상관없음", CORRECT: "정답!" };
const VCLASS = { YES: "yes", NO: "no", IRRELEVANT: "irrelevant", CORRECT: "correct" };

function me() {
  return (typeof getNickname === "function" && getNickname()) || "익명";
}

async function loadPuzzleOptions() {
  const res = await fetch("/api/puzzles");
  if (!res.ok) return;
  const puzzles = await res.json();
  const sel = document.getElementById("puzzle-select");
  puzzles.forEach(p => {
    const o = document.createElement("option");
    o.value = p.id;
    o.textContent = `${p.title} [${p.difficulty}]`;
    sel.appendChild(o);
  });
}

document.querySelectorAll('input[name="src"]').forEach(r => {
  r.addEventListener("change", () => {
    const custom = document.querySelector('input[name="src"]:checked').value === "custom";
    document.getElementById("custom-fields").classList.toggle("hidden", !custom);
    document.getElementById("puzzle-select").disabled = custom;
  });
});

async function createRoom() {
  const src = document.querySelector('input[name="src"]:checked').value;
  const body = { hostName: me() };
  if (src === "existing") {
    body.puzzleId = Number(document.getElementById("puzzle-select").value);
  } else {
    body.title = document.getElementById("custom-title").value.trim();
    body.scenario = document.getElementById("custom-scenario").value.trim();
    body.solution = document.getElementById("custom-solution").value.trim();
    if (!body.scenario || !body.solution) { alert("상황과 정답을 입력하세요."); return; }
  }
  const res = await fetch("/api/rooms", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!res.ok) { alert("방 생성에 실패했습니다."); return; }
  const data = await res.json();
  isHost = true;
  enterRoom(data.code, data.title, data.scenario, data.solution);
}

async function joinRoom() {
  const code = document.getElementById("join-code").value.trim().toUpperCase();
  if (!code) return;
  const res = await fetch(`/api/rooms/${code}`);
  if (!res.ok) { alert("그런 방이 없습니다. 코드를 확인하세요."); return; }
  const info = await res.json();
  isHost = (info.hostName === me());
  enterRoom(info.code, info.title, info.scenario, null);
}

function enterRoom(code, title, scenario, solution) {
  myCode = code;
  document.getElementById("room-code-badge").textContent = "방 코드: " + code;
  document.getElementById("room-title").textContent = title;
  document.getElementById("room-scenario").textContent = scenario;
  document.getElementById("room-log").textContent = "";

  if (isHost && solution) {
    document.getElementById("host-solution-text").textContent = solution;
    document.getElementById("host-solution").classList.remove("hidden");
  }
  document.getElementById("host-controls").classList.toggle("hidden", !isHost);
  document.getElementById("participant-composer").classList.toggle("hidden", isHost);

  lobby.classList.add("hidden");
  roomView.classList.remove("hidden");

  const proto = location.protocol === "https:" ? "wss" : "ws";
  ws = new WebSocket(`${proto}://${location.host}/ws/room/${code}`);
  ws.onopen = () => ws.send(JSON.stringify({ type: "join", nickname: me() }));
  ws.onmessage = (e) => handleEvent(JSON.parse(e.data));
  ws.onclose = () => appendSystem("연결이 종료되었습니다.");
}

function handleEvent(ev) {
  if (ev.type === "system") appendSystem(ev.text);
  else if (ev.type === "question") appendMsg("me", ev.nickname, ev.text);
  else if (ev.type === "answer") appendVerdict(ev.verdict);
  else if (ev.type === "reveal") {
    appendSystem("📖 정답이 공개되었습니다.");
    appendSolution(ev.solution);
    endGame();
  }
}

function row(side, name, contentEl) {
  const r = document.createElement("div"); r.className = "msg";
  const av = document.createElement("div"); av.className = "msg-avatar " + side;
  av.textContent = name.charAt(0).toUpperCase();
  const body = document.createElement("div");
  const nm = document.createElement("div"); nm.className = "msg-name"; nm.textContent = name;
  body.append(nm, contentEl);
  r.append(av, body);
  const log = document.getElementById("room-log");
  log.appendChild(r);
  const c = document.querySelector(".content"); if (c) c.scrollTop = c.scrollHeight;
}
function textDiv(t) { const d = document.createElement("div"); d.className = "msg-text"; d.textContent = t; return d; }
function appendMsg(side, name, text) { row(side, name, textDiv(text)); }
function appendSystem(text) {
  const p = document.createElement("p"); p.className = "muted"; p.style.textAlign = "center"; p.textContent = text;
  document.getElementById("room-log").appendChild(p);
}
function appendVerdict(v) {
  const pill = document.createElement("span");
  pill.className = "verdict " + (VCLASS[v] || "unknown");
  pill.textContent = VERDICT[v] || v;
  row("bot", "출제자", pill);
}
function appendSolution(text) {
  const box = document.createElement("div"); box.className = "msg-text";
  box.style.background = "var(--info-bg)"; box.style.borderRadius = "8px";
  box.style.padding = "10px 12px"; box.style.lineHeight = "1.7";
  box.textContent = text;
  row("bot", "출제자", box);
}
function endGame() {
  document.getElementById("room-input") && (document.getElementById("room-input").disabled = true);
  document.querySelectorAll("#host-controls button").forEach(b => b.disabled = true);
  const send = document.getElementById("room-send"); if (send) send.disabled = true;
}

function sendQuestion() {
  const input = document.getElementById("room-input");
  const t = input.value.trim();
  if (!t || !ws) return;
  ws.send(JSON.stringify({ type: "ask", nickname: me(), text: t }));
  input.value = "";
}
function sendAnswer(verdict) {
  if (ws) ws.send(JSON.stringify({ type: "answer", nickname: me(), verdict }));
}
function leaveRoom() {
  if (ws) ws.close();
  roomView.classList.add("hidden");
  lobby.classList.remove("hidden");
}

document.getElementById("create-btn").addEventListener("click", createRoom);
document.getElementById("join-btn").addEventListener("click", joinRoom);
document.getElementById("room-send").addEventListener("click", sendQuestion);
document.getElementById("room-input").addEventListener("keydown", e => { if (e.key === "Enter") sendQuestion(); });
document.querySelectorAll("#host-controls .ans").forEach(b => {
  b.addEventListener("click", () => sendAnswer(b.dataset.v));
});
document.getElementById("reveal-btn").addEventListener("click", () => {
  if (ws) ws.send(JSON.stringify({ type: "reveal", nickname: me() }));
});

loadPuzzleOptions();
```

- [ ] **Step 4: 문법 확인 + 커밋**

Run: `node --check src/main/resources/static/js/room.js`
```bash
git add src/main/resources/static/room.html src/main/resources/static/js/room.js src/main/resources/static/index.html src/main/resources/static/classic.html src/main/resources/static/ai.html
git commit -m "feat: add multiplayer room UI (lobby + live room) and enable nav"
```

---

### Task 6: 통합 점검

- [ ] **Step 1: 전체 테스트** — `./gradlew test` → `BUILD SUCCESSFUL`.

- [ ] **Step 2: 수동 2-탭 점검** — 앱을 백그라운드로 띄우고(`bootRun`), 브라우저 탭 2개로:
  - 탭A(출제자): `/room.html` → 닉네임 설정 → 기존 문제 선택 → 방 만들기 → 방 코드 확인, 정답(나만 봄) 패널 표시.
  - 탭B(참가자): `/room.html` → 다른 닉네임 → 방 코드 입력 → 입장. 두 탭 모두 입장 알림.
  - 탭B에서 질문 전송 → 양쪽에 질문 표시. 탭A에서 [예]/[아니오] → 양쪽에 판정 배지. 탭A [정답 처리] 또는 [정답 공개] → 양쪽에 해설 공개 + 입력 비활성화.
  - 커스텀 출제도 한 번 확인(직접 출제 라디오 → 상황/정답 입력 → 방 만들기).

- [ ] **Step 3: 최종 커밋(있으면)** — `git add -A && git commit -m "chore: multiplayer mode complete" || echo "변경 없음"`

---

## 추가 요구: 힌트 (방장 제공, 멀티 한정 — 이 플랜에 포함)
- `Room`에 `hintsUsed`(int, 최대 3) 추가: `canHint()`, `useHint()`, `getHintsUsed()`.
- 프로토콜 추가: 클라→서버 `{type:"hint", nickname, text}` (방장만); 서버→클라 `{type:"hint", text, count, max:3}`.
- `RoomSocketHandler`에 `hint` 케이스: 방장 && 미종료 && `canHint()` && text 비어있지 않으면 → `useHint()` 후 전원 broadcast.
- `room.html` 출제자 컨트롤에 힌트 입력 + `힌트 주기 (n/3)` 버튼. `room.js`에서 전송/렌더(출제자 힌트 메시지)/카운트, 3회 시 비활성화.

## 다음 (범위 밖 — 후속 플랜 4: 클래식·AI 힌트)
- **① 클래식**: `Puzzle`에 사전작성 힌트 3개(hint1/2/3) + 26문제 힌트 작성(서브에이전트) + `GET /api/puzzles/{id}/hint/{n}` + 힌트 버튼.
- **② AI**: `AiJudgeService.hint(puzzleId)`로 LLM이 비누설 힌트 생성 + `POST /api/ai/{id}/hint` + 힌트 버튼(3회).

## 다음 (범위 밖 — 플랜 5: 배포)
- Dockerfile, Render/Railway + Supabase Postgres(prod), Gemini로 AI 제공자 교체, WebSocket 배포 확인(wss), prod H2콘솔 비활성화.
