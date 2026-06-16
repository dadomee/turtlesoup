# 바다거북스프 — ② AI 출제자 모드 구현 계획 (플랜 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 플레이어가 자유롭게 질문하면 AI(Ollama qwen2.5:3b)가 정답을 기준으로 "예 / 아니오 / 상관없음 / 정답"으로만 판정하는 AI 출제자 모드를 추가하고, 정답을 맞히면 기록을 남긴다.

**Architecture:** Spring AI `ChatClient`(Ollama 백엔드)로 LLM을 호출한다. LLM이 닿지 않는 순수 로직(판정어 파싱, 프롬프트 구성)은 단위 테스트로, 실제 LLM 호출은 통합/수동 테스트로 검증한다. 채팅은 무상태(stateless): 질문마다 (문제+정답+질문)을 보내 독립 판정한다. 프론트(바닐라 JS)가 대화 로그와 질문 수를 관리하고, 정답 시 기록을 서버에 남긴다. 제공자 추상화(ChatClient)는 유지하여 배포 시 Gemini로 교체 가능(플랜 4).

**Tech Stack:** Spring Boot 3.5.15, Spring AI 1.0.9 (`spring-ai-starter-model-ollama`), Ollama `qwen2.5:3b` @ localhost:11434, JPA(H2), JUnit 5, 바닐라 JS

**전제:** 플랜 1 완료(Boot 3.5.15, `puzzle` 패키지, REST API, 정적 프론트). 로컬에 Ollama 실행 중 + `qwen2.5:3b` 받아져 있어야 통합 테스트 가능(`ollama pull qwen2.5:3b`). askmynotes가 동일한 Spring AI + Ollama 구성을 쓰므로 ChatClient 배선은 그쪽 패턴을 참고한다.

---

## File Structure

```
src/main/java/com/turtlesoup/
├─ ai/
│  ├─ Verdict.java                  판정 결과 enum (Task 2)
│  ├─ VerdictParser.java            LLM 원문 → Verdict 파싱 (순수, Task 2)
│  ├─ AiJudgeService.java           프롬프트 구성 + ChatClient 호출 + 파싱 (Task 3)
│  ├─ AiController.java             POST /api/ai/{puzzleId}/ask (Task 4)
│  └─ dto/AskRequest.java, dto/AskResponse.java   (Task 4)
├─ config/
│  └─ ChatClientConfig.java         ChatClient 빈 (Task 1)
└─ history/
   ├─ GameMode.java                 CLASSIC / AI enum (Task 5)
   ├─ PlayHistory.java              기록 엔티티 (Task 5)
   ├─ PlayHistoryRepository.java    (Task 5)
   ├─ PlayHistoryService.java       기록 저장 (Task 5)
   ├─ HistoryController.java        POST /api/ai/{puzzleId}/solve (Task 5)
   └─ dto/SolveRequest.java         (Task 5)

src/main/resources/
├─ application.yml                  spring.ai.ollama 설정 추가 (Task 1)
└─ static/
   ├─ ai.html                       AI 채팅 페이지 (Task 6)
   ├─ js/ai.js                      채팅 로직 (Task 6)
   └─ index.html                    "② AI 출제자" 링크 활성화 (Task 6, 수정)

src/test/java/com/turtlesoup/
├─ ai/VerdictParserTest.java        (Task 2)
├─ ai/AiControllerTest.java         (Task 4)
└─ history/PlayHistoryRepositoryTest.java, history/HistoryControllerTest.java (Task 5)
```

---

### Task 1: Spring AI(Ollama) 의존성 + 설정 + ChatClient 빈

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/turtlesoup/config/ChatClientConfig.java`

- [ ] **Step 1: build.gradle에 Spring AI 추가**

`build.gradle`의 `dependencies { ... }` 위에 Spring AI BOM 버전을 선언하고, 의존성을 추가한다. 최종 `build.gradle`:

```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '3.5.15'
	id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.turtlesoup'
version = '0.0.1-SNAPSHOT'

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

ext {
	springAiVersion = '1.0.9'
}

dependencies {
	implementation platform("org.springframework.ai:spring-ai-bom:${springAiVersion}")
	implementation 'org.springframework.ai:spring-ai-starter-model-ollama'

	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	runtimeOnly 'com.h2database:h2'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
	useJUnitPlatform()
}
```

- [ ] **Step 2: application.yml에 Ollama 설정 추가**

`src/main/resources/application.yml`을 아래로 교체(기존 profiles/server 유지 + ai 추가). askmynotes 설정을 미러링:

```yaml
spring:
  profiles:
    active: local
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen2.5:3b
          temperature: 0.2
          repeat-penalty: 1.1
          num-predict: 200
          keep-alive: 10m
      init:
        pull-model-strategy: never
server:
  port: 8080
```

- [ ] **Step 3: ChatClient 빈 작성**

`src/main/java/com/turtlesoup/config/ChatClientConfig.java`:

```java
package com.turtlesoup.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
```

(참고: Spring AI Ollama 스타터가 `ChatModel`과 `ChatClient.Builder`를 자동 구성한다. askmynotes의 ChatClient 사용 패턴과 동일.)

- [ ] **Step 4: 컨텍스트 기동 확인 (Ollama 없이도 떠야 함)**

`pull-model-strategy: never`라 startup에서 모델을 받지 않으므로 Ollama가 꺼져 있어도 컨텍스트는 로드되어야 한다.

Run: `./gradlew test`
Expected: 기존 플랜 1 테스트 전부 통과(`@SpringBootTest` 포함). `BUILD SUCCESSFUL`. (만약 Ollama 미기동으로 컨텍스트 로드가 실패하면 BLOCKED로 보고 — 그 경우 `ollama serve` 실행 후 재시도.)

- [ ] **Step 5: 커밋**

```bash
git add build.gradle src/main/resources/application.yml src/main/java/com/turtlesoup/config/ChatClientConfig.java
git commit -m "feat: add Spring AI Ollama (qwen2.5:3b) + ChatClient bean"
```

---

### Task 2: Verdict enum + VerdictParser (TDD, 순수 로직)

**Files:**
- Create: `src/main/java/com/turtlesoup/ai/Verdict.java`
- Create: `src/main/java/com/turtlesoup/ai/VerdictParser.java`
- Test: `src/test/java/com/turtlesoup/ai/VerdictParserTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/turtlesoup/ai/VerdictParserTest.java`:

```java
package com.turtlesoup.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictParserTest {

    @Test
    void parsesYes() {
        assertThat(VerdictParser.parse("예")).isEqualTo(Verdict.YES);
        assertThat(VerdictParser.parse("네")).isEqualTo(Verdict.YES);
    }

    @Test
    void parsesNo() {
        assertThat(VerdictParser.parse("아니오")).isEqualTo(Verdict.NO);
        assertThat(VerdictParser.parse("아니요")).isEqualTo(Verdict.NO);
    }

    @Test
    void parsesIrrelevant() {
        assertThat(VerdictParser.parse("상관없음")).isEqualTo(Verdict.IRRELEVANT);
        assertThat(VerdictParser.parse("관계없음")).isEqualTo(Verdict.IRRELEVANT);
    }

    @Test
    void parsesCorrect() {
        assertThat(VerdictParser.parse("정답")).isEqualTo(Verdict.CORRECT);
        assertThat(VerdictParser.parse("정답입니다")).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void noTakesPriorityOverCorrectWhenBothPresent() {
        // "정답이 아닙니다" 처럼 둘 다 포함되면 부정으로 본다
        assertThat(VerdictParser.parse("정답이 아닙니다")).isEqualTo(Verdict.NO);
    }

    @Test
    void trimsAndIgnoresSurroundingText() {
        assertThat(VerdictParser.parse("  예.  ")).isEqualTo(Verdict.YES);
    }

    @Test
    void unknownWhenNoKeyword() {
        assertThat(VerdictParser.parse("글쎄요")).isEqualTo(Verdict.UNKNOWN);
        assertThat(VerdictParser.parse("")).isEqualTo(Verdict.UNKNOWN);
        assertThat(VerdictParser.parse(null)).isEqualTo(Verdict.UNKNOWN);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests VerdictParserTest`
Expected: 컴파일 실패 — `Verdict`, `VerdictParser` 없음.

- [ ] **Step 3: Verdict enum 작성**

`src/main/java/com/turtlesoup/ai/Verdict.java`:

```java
package com.turtlesoup.ai;

public enum Verdict {
    YES, NO, IRRELEVANT, CORRECT, UNKNOWN
}
```

- [ ] **Step 4: VerdictParser 작성**

판정 우선순위: 부정("아니") → 정답("정답") → 무관("상관"/"관계") → 긍정("예"/"네") → UNKNOWN.
(부정을 정답보다 먼저 검사해 "정답이 아닙니다"를 올바르게 NO로 처리한다.)

`src/main/java/com/turtlesoup/ai/VerdictParser.java`:

```java
package com.turtlesoup.ai;

public final class VerdictParser {

    private VerdictParser() {
    }

    public static Verdict parse(String raw) {
        if (raw == null) {
            return Verdict.UNKNOWN;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return Verdict.UNKNOWN;
        }
        if (s.contains("아니")) {
            return Verdict.NO;
        }
        if (s.contains("정답")) {
            return Verdict.CORRECT;
        }
        if (s.contains("상관") || s.contains("관계")) {
            return Verdict.IRRELEVANT;
        }
        if (s.contains("예") || s.contains("네")) {
            return Verdict.YES;
        }
        return Verdict.UNKNOWN;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests VerdictParserTest`
Expected: PASS (7개 테스트).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/turtlesoup/ai/Verdict.java src/main/java/com/turtlesoup/ai/VerdictParser.java src/test/java/com/turtlesoup/ai/VerdictParserTest.java
git commit -m "feat: add Verdict enum and VerdictParser (pure parsing)"
```

---

### Task 3: AiJudgeService (프롬프트 구성 + ChatClient 호출 + 파싱)

**Files:**
- Create: `src/main/java/com/turtlesoup/ai/AiJudgeService.java`

이 서비스는 LLM을 실제로 호출하므로 단위 테스트로 검증하지 않는다(브리틀한 ChatClient 모킹 회피). 순수 로직(VerdictParser)은 Task 2에서, 실제 호출은 Task 7 통합/수동 테스트에서 검증한다. 컨트롤러 테스트(Task 4)는 이 서비스를 목킹한다.

- [ ] **Step 1: AiJudgeService 작성**

문제 조회는 기존 `PuzzleRepository`를 사용한다(정답 텍스트가 필요하므로 엔티티 직접 조회). 없는 puzzleId면 `PuzzleNotFoundException`.

`src/main/java/com/turtlesoup/ai/AiJudgeService.java`:

```java
package com.turtlesoup.ai;

import com.turtlesoup.puzzle.Puzzle;
import com.turtlesoup.puzzle.PuzzleNotFoundException;
import com.turtlesoup.puzzle.PuzzleRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiJudgeService {

    private static final String SYSTEM_PROMPT = """
        당신은 '바다거북스프'(수평적 사고 추리 게임)의 출제자입니다.
        아래 [문제]와 [정답]을 알고 있습니다. 플레이어가 질문하면, 정답에 비추어 아래 네 가지 중 하나로만 답하세요.

        - "예": 질문 내용이 정답에 비추어 맞을 때
        - "아니오": 질문 내용이 정답에 비추어 틀릴 때
        - "상관없음": 질문이 정답과 관련이 없거나 판단할 수 없을 때
        - "정답": 플레이어가 사건의 핵심 진상을 충분히 맞혔을 때

        규칙:
        - 반드시 위 네 단어 중 하나로만 답하세요. 다른 설명, 문장, 이모지를 절대 붙이지 마세요.
        - 정답을 직접 누설하지 마세요.

        [문제]
        %s

        [정답]
        %s
        """;

    private final ChatClient chatClient;
    private final PuzzleRepository puzzleRepository;

    public AiJudgeService(ChatClient chatClient, PuzzleRepository puzzleRepository) {
        this.chatClient = chatClient;
        this.puzzleRepository = puzzleRepository;
    }

    public Verdict judge(Long puzzleId, String question) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
            .orElseThrow(() -> new PuzzleNotFoundException(puzzleId));

        String system = String.format(SYSTEM_PROMPT, puzzle.getScenario(), puzzle.getSolution());

        String raw = chatClient.prompt()
            .system(system)
            .user("[플레이어 질문] " + question)
            .call()
            .content();

        return VerdictParser.parse(raw);
    }
}
```

(참고: `chatClient.prompt().system(..).user(..).call().content()`는 Spring AI 1.0.x의 표준 호출 형태다. 시그니처가 다르면 askmynotes의 ChatClient 사용부를 참고해 맞춘다.)

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/turtlesoup/ai/AiJudgeService.java
git commit -m "feat: add AiJudgeService (Ollama judge with system prompt)"
```

---

### Task 4: AiController + DTO (TDD, 서비스 목킹)

**Files:**
- Create: `src/main/java/com/turtlesoup/ai/dto/AskRequest.java`
- Create: `src/main/java/com/turtlesoup/ai/dto/AskResponse.java`
- Create: `src/main/java/com/turtlesoup/ai/AiController.java`
- Test: `src/test/java/com/turtlesoup/ai/AiControllerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/turtlesoup/ai/AiControllerTest.java`:

```java
package com.turtlesoup.ai;

import com.turtlesoup.puzzle.PuzzleNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
class AiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AiJudgeService service;

    @Test
    void askReturnsVerdict() throws Exception {
        when(service.judge(eq(1L), anyString())).thenReturn(Verdict.YES);

        mockMvc.perform(post("/api/ai/1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"주인공이 죽었나요?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verdict").value("YES"));
    }

    @Test
    void missingPuzzleReturns404() throws Exception {
        when(service.judge(eq(99L), anyString())).thenThrow(new PuzzleNotFoundException(99L));

        mockMvc.perform(post("/api/ai/99/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"x\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void blankQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/ai/1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests AiControllerTest`
Expected: 컴파일 실패 — `AiController`, DTO 없음.

- [ ] **Step 3: DTO 작성**

`src/main/java/com/turtlesoup/ai/dto/AskRequest.java`:

```java
package com.turtlesoup.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(@NotBlank String question) {
}
```

`src/main/java/com/turtlesoup/ai/dto/AskResponse.java`:

```java
package com.turtlesoup.ai.dto;

import com.turtlesoup.ai.Verdict;

public record AskResponse(Verdict verdict) {
}
```

- [ ] **Step 4: AiController 작성**

`PuzzleNotFoundException` → 404는 기존 `PuzzleController`의 핸들러가 같은 컨트롤러에만 적용되므로, AiController에도 동일 핸들러를 둔다(또는 공용 `@RestControllerAdvice`로 추출 가능하나 YAGNI — 여기선 로컬 핸들러).

`src/main/java/com/turtlesoup/ai/AiController.java`:

```java
package com.turtlesoup.ai;

import com.turtlesoup.ai.dto.AskRequest;
import com.turtlesoup.ai.dto.AskResponse;
import com.turtlesoup.puzzle.PuzzleNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiJudgeService service;

    public AiController(AiJudgeService service) {
        this.service = service;
    }

    @PostMapping("/{puzzleId}/ask")
    public AskResponse ask(@PathVariable Long puzzleId, @Valid @RequestBody AskRequest request) {
        return new AskResponse(service.judge(puzzleId, request.question()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PuzzleNotFoundException.class)
    public Map<String, String> handleNotFound(PuzzleNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests AiControllerTest`
Expected: PASS (3개 테스트). 만약 `blankQuestionReturns400`이 실패하면 `@Valid` 검증이 동작하도록 `spring-boot-starter-validation`이 있는지 확인(플랜 1에 포함됨).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/turtlesoup/ai/dto/ src/main/java/com/turtlesoup/ai/AiController.java src/test/java/com/turtlesoup/ai/AiControllerTest.java
git commit -m "feat: add AiController POST /api/ai/{id}/ask"
```

---

### Task 5: play_history 기록 (엔티티 + 리포지토리 + solve 엔드포인트)

**Files:**
- Create: `src/main/java/com/turtlesoup/history/GameMode.java`
- Create: `src/main/java/com/turtlesoup/history/PlayHistory.java`
- Create: `src/main/java/com/turtlesoup/history/PlayHistoryRepository.java`
- Create: `src/main/java/com/turtlesoup/history/PlayHistoryService.java`
- Create: `src/main/java/com/turtlesoup/history/dto/SolveRequest.java`
- Create: `src/main/java/com/turtlesoup/history/HistoryController.java`
- Test: `src/test/java/com/turtlesoup/history/PlayHistoryRepositoryTest.java`
- Test: `src/test/java/com/turtlesoup/history/HistoryControllerTest.java`

- [ ] **Step 1: GameMode + PlayHistory 엔티티 작성**

`src/main/java/com/turtlesoup/history/GameMode.java`:

```java
package com.turtlesoup.history;

public enum GameMode {
    CLASSIC, AI
}
```

`src/main/java/com/turtlesoup/history/PlayHistory.java`:

```java
package com.turtlesoup.history;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "play_history")
public class PlayHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long puzzleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameMode mode;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private boolean solved;

    @Column(nullable = false)
    private Instant createdAt;

    protected PlayHistory() {
    }

    public PlayHistory(Long puzzleId, GameMode mode, String nickname, int questionCount, boolean solved) {
        this.puzzleId = puzzleId;
        this.mode = mode;
        this.nickname = nickname;
        this.questionCount = questionCount;
        this.solved = solved;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPuzzleId() { return puzzleId; }
    public GameMode getMode() { return mode; }
    public String getNickname() { return nickname; }
    public int getQuestionCount() { return questionCount; }
    public boolean isSolved() { return solved; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 2: 리포지토리 + 실패하는 리포지토리 테스트 (TDD)**

`src/test/java/com/turtlesoup/history/PlayHistoryRepositoryTest.java`:

```java
package com.turtlesoup.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PlayHistoryRepositoryTest {

    @Autowired
    PlayHistoryRepository repository;

    @Test
    void savesPlayHistory() {
        PlayHistory saved = repository.save(
            new PlayHistory(1L, GameMode.AI, "다솜", 5, true));

        PlayHistory found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getNickname()).isEqualTo("다솜");
        assertThat(found.getQuestionCount()).isEqualTo(5);
        assertThat(found.isSolved()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
```

Run `./gradlew test --tests PlayHistoryRepositoryTest` → 컴파일 실패 확인. 그 다음 `src/main/java/com/turtlesoup/history/PlayHistoryRepository.java`:

```java
package com.turtlesoup.history;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayHistoryRepository extends JpaRepository<PlayHistory, Long> {
}
```

Run `./gradlew test --tests PlayHistoryRepositoryTest` → PASS.

- [ ] **Step 3: PlayHistoryService + SolveRequest DTO 작성**

`src/main/java/com/turtlesoup/history/dto/SolveRequest.java`:

```java
package com.turtlesoup.history.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SolveRequest(@NotBlank String nickname, @Min(0) int questionCount) {
}
```

`src/main/java/com/turtlesoup/history/PlayHistoryService.java`:

```java
package com.turtlesoup.history;

import org.springframework.stereotype.Service;

@Service
public class PlayHistoryService {

    private final PlayHistoryRepository repository;

    public PlayHistoryService(PlayHistoryRepository repository) {
        this.repository = repository;
    }

    public void recordSolve(Long puzzleId, GameMode mode, String nickname, int questionCount) {
        repository.save(new PlayHistory(puzzleId, mode, nickname, questionCount, true));
    }
}
```

- [ ] **Step 4: 실패하는 컨트롤러 테스트 작성**

`src/test/java/com/turtlesoup/history/HistoryControllerTest.java`:

```java
package com.turtlesoup.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
class HistoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PlayHistoryService service;

    @Test
    void recordsSolve() throws Exception {
        mockMvc.perform(post("/api/ai/1/solve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"다솜\",\"questionCount\":5}"))
            .andExpect(status().isOk());

        verify(service).recordSolve(eq(1L), eq(GameMode.AI), eq("다솜"), eq(5));
    }

    @Test
    void blankNicknameReturns400() throws Exception {
        mockMvc.perform(post("/api/ai/1/solve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\",\"questionCount\":5}"))
            .andExpect(status().isBadRequest());
    }
}
```

Run `./gradlew test --tests HistoryControllerTest` → 컴파일 실패 확인.

- [ ] **Step 5: HistoryController 작성**

`src/main/java/com/turtlesoup/history/HistoryController.java`:

```java
package com.turtlesoup.history;

import com.turtlesoup.history.dto.SolveRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class HistoryController {

    private final PlayHistoryService service;

    public HistoryController(PlayHistoryService service) {
        this.service = service;
    }

    @PostMapping("/{puzzleId}/solve")
    public void solve(@PathVariable Long puzzleId, @Valid @RequestBody SolveRequest request) {
        service.recordSolve(puzzleId, GameMode.AI, request.nickname(), request.questionCount());
    }
}
```

Run `./gradlew test --tests HistoryControllerTest` → PASS (2개).

- [ ] **Step 6: 전체 테스트 + 커밋**

Run: `./gradlew test` → 전부 PASS.

```bash
git add src/main/java/com/turtlesoup/history/ src/test/java/com/turtlesoup/history/
git commit -m "feat: record solved games in play_history (AI mode)"
```

---

### Task 6: 프론트 — AI 채팅 페이지 + 모드 선택 링크 활성화

**Files:**
- Create: `src/main/resources/static/ai.html`
- Create: `src/main/resources/static/js/ai.js`
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: index.html에서 ② AI 출제자 링크 활성화**

`src/main/resources/static/index.html`의 `mode-select` 카드에서 안내 문구를 줄이고 AI 버튼을 추가한다. 아래 블록을 찾아서:

```html
    <p><button onclick="location.href='/classic.html'">① 미리 만든 문제 풀기</button></p>
    <p class="muted">② AI 출제자, ③ 멀티플레이는 곧 추가됩니다.</p>
```

다음으로 교체:

```html
    <p><button onclick="location.href='/classic.html'">① 미리 만든 문제 풀기</button></p>
    <p><button onclick="location.href='/ai.html'">② AI 출제자에게 질문하기</button></p>
    <p class="muted">③ 멀티플레이는 곧 추가됩니다.</p>
```

- [ ] **Step 2: ai.html 작성**

`src/main/resources/static/ai.html`:

```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>바다거북스프 — AI 출제자</title>
  <link rel="stylesheet" href="/css/style.css">
</head>
<body>
  <h1>🐢 AI 출제자</h1>
  <p><button onclick="location.href='/'" style="background:#888">← 모드 선택</button></p>

  <div id="list-view">
    <h2>문제 목록</h2>
    <div id="puzzle-list"></div>
  </div>

  <div id="play-view" class="hidden">
    <div class="card">
      <h2 id="play-title"></h2>
      <p class="muted" id="play-meta"></p>
      <p id="play-scenario"></p>
    </div>

    <div class="card">
      <p class="muted">예/아니오로 답할 수 있는 질문을 던져 진상을 추리하세요. 핵심을 맞히면 "정답!"이 나옵니다.</p>
      <div id="chat-log"></div>
      <div id="ask-row">
        <input type="text" id="question-input" placeholder="예: 주인공이 죽었나요?">
        <button id="ask-btn">질문</button>
      </div>
      <p class="muted">질문 수: <span id="q-count">0</span></p>
    </div>

    <p><button onclick="showList()" style="background:#888">← 목록으로</button></p>
  </div>

  <script src="/js/nickname.js"></script>
  <script src="/js/ai.js"></script>
</body>
</html>
```

- [ ] **Step 3: ai.js 작성**

판정 코드(YES/NO/IRRELEVANT/CORRECT/UNKNOWN)를 한국어 배지로 표시하고, CORRECT면 solve를 기록한다. 질문 수는 클라이언트가 센다. 닉네임은 `nickname.js`의 `getNickname()`(없으면 "익명").

`src/main/resources/static/js/ai.js`:

```javascript
const listView = document.getElementById("list-view");
const playView = document.getElementById("play-view");

let currentPuzzleId = null;
let questionCount = 0;
let solved = false;

const VERDICT_LABEL = {
  YES: "예 ✅",
  NO: "아니오 ❌",
  IRRELEVANT: "상관없음 🤷",
  CORRECT: "정답! 🎉",
  UNKNOWN: "잘 모르겠어요 — 다르게 물어봐 주세요"
};

async function loadList() {
  const res = await fetch("/api/puzzles");
  if (!res.ok) {
    document.getElementById("puzzle-list").textContent = "문제 목록을 불러오지 못했습니다.";
    return;
  }
  const puzzles = await res.json();
  const container = document.getElementById("puzzle-list");
  container.textContent = "";
  puzzles.forEach(p => {
    const div = document.createElement("div");
    div.className = "card";
    const title = document.createElement("b");
    title.textContent = p.title;
    const meta = document.createElement("span");
    meta.className = "muted";
    meta.textContent = ` [${p.difficulty}]`;
    const btn = document.createElement("button");
    btn.textContent = "이 문제로 질문";
    btn.style.marginTop = "8px";
    btn.style.display = "block";
    btn.addEventListener("click", () => openPuzzle(p.id));
    div.append(title, meta, btn);
    container.appendChild(div);
  });
}

async function openPuzzle(id) {
  const res = await fetch(`/api/puzzles/${id}`);
  if (!res.ok) { alert("문제를 불러오지 못했습니다."); return; }
  const p = await res.json();

  currentPuzzleId = id;
  questionCount = 0;
  solved = false;
  document.getElementById("q-count").textContent = "0";
  document.getElementById("chat-log").textContent = "";
  document.getElementById("question-input").value = "";
  document.getElementById("question-input").disabled = false;
  document.getElementById("ask-btn").disabled = false;

  document.getElementById("play-title").textContent = p.title;
  document.getElementById("play-meta").textContent = `난이도: ${p.difficulty}`;
  document.getElementById("play-scenario").textContent = p.scenario;

  listView.classList.add("hidden");
  playView.classList.remove("hidden");
}

function appendChat(who, text) {
  const log = document.getElementById("chat-log");
  const line = document.createElement("p");
  const label = document.createElement("b");
  label.textContent = who + ": ";
  line.appendChild(label);
  line.appendChild(document.createTextNode(text));
  log.appendChild(line);
}

async function ask() {
  if (solved) return;
  const input = document.getElementById("question-input");
  const q = input.value.trim();
  if (!q) return;

  appendChat("나", q);
  input.value = "";
  questionCount += 1;
  document.getElementById("q-count").textContent = String(questionCount);

  const res = await fetch(`/api/ai/${currentPuzzleId}/ask`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: q })
  });
  if (!res.ok) {
    appendChat("AI", "답변을 가져오지 못했습니다. (Ollama가 켜져 있는지 확인하세요)");
    return;
  }
  const data = await res.json();
  appendChat("AI", VERDICT_LABEL[data.verdict] || data.verdict);

  if (data.verdict === "CORRECT") {
    solved = true;
    document.getElementById("question-input").disabled = true;
    document.getElementById("ask-btn").disabled = true;
    recordSolve();
  }
}

async function recordSolve() {
  const nickname = (typeof getNickname === "function" && getNickname()) || "익명";
  await fetch(`/api/ai/${currentPuzzleId}/solve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ nickname, questionCount })
  });
}

function showList() {
  playView.classList.add("hidden");
  listView.classList.remove("hidden");
}

document.getElementById("ask-btn").addEventListener("click", ask);
document.getElementById("question-input").addEventListener("keydown", e => {
  if (e.key === "Enter") ask();
});

loadList();
```

- [ ] **Step 4: 문법 확인**

Run: `node --check src/main/resources/static/js/ai.js`
Expected: 에러 없음.

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/static/ai.html src/main/resources/static/js/ai.js src/main/resources/static/index.html
git commit -m "feat: add AI judge chat page and enable mode link"
```

---

### Task 7: 통합 점검 (단위는 자동, 실제 LLM은 수동)

- [ ] **Step 1: 전체 테스트 (LLM 불필요 — 전부 목킹/순수)**

Run: `./gradlew test`
Expected: 모든 테스트 통과. `BUILD SUCCESSFUL`.

- [ ] **Step 2: 실제 Ollama 연동 수동 점검**

전제: `ollama serve` 실행 중 + `ollama pull qwen2.5:3b` 완료.

앱을 백그라운드로 띄우고(포그라운드 bootRun은 멈추니 금지) API를 직접 호출:

```bash
./gradlew bootRun > /tmp/ts-ai.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 40); do grep -q "Started TurtlesoupApplication" /tmp/ts-ai.log && break; sleep 1; done
echo "== 질문(정답과 무관) =="
curl -s -X POST http://localhost:8080/api/ai/1/ask -H "Content-Type: application/json" -d '{"question":"주인공이 여자인가요?"}'
echo; echo "== 질문(정답 근접) =="
curl -s -X POST http://localhost:8080/api/ai/1/ask -H "Content-Type: application/json" -d '{"question":"예전에 사람 고기를 먹은 적이 있나요?"}'
echo; echo "== solve 기록 =="
curl -s -o /dev/null -w "solve_status=%{http_code}\n" -X POST http://localhost:8080/api/ai/1/solve -H "Content-Type: application/json" -d '{"nickname":"다솜","questionCount":3}'
kill $BOOT_PID 2>/dev/null
```

Expected: 첫 호출은 `{"verdict":"YES"|"NO"|"IRRELEVANT"}` 중 하나, 두 번째는 핵심에 근접하면 `CORRECT` 가능, solve는 200. (모델 판정은 확률적이라 정확한 값보다 "네 종류 중 하나가 정상 반환되는지"를 본다.)

- [ ] **Step 3: 브라우저 흐름 수동 점검 (선택)**

`http://localhost:8080/` → 닉네임 → "② AI 출제자에게 질문하기" → 문제 선택 → 질문 입력 → 판정 배지 표시 → 핵심 맞히면 "정답! 🎉" + 입력 비활성화.

- [ ] **Step 4: 최종 커밋 (변경 있으면)**

```bash
git add -A && git commit -m "chore: AI judge mode complete" || echo "변경 없음"
```

---

## 다음 플랜 (범위 밖)

- **플랜 3 — ③ 멀티플레이**: Spring WebSocket(STOMP), `RoomService`(메모리), 방 생성/입장/실시간 채팅
- **플랜 4 — 배포**: Dockerfile, Render/Railway, Supabase Postgres(prod 프로파일), **Gemini 무료 티어로 제공자 교체**(ChatClient 추상화 유지 → 의존성/설정만 변경), prod 프로파일에서 H2 콘솔 비활성화
