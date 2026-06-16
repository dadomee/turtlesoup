# 바다거북스프 — 뼈대 + 클래식 모드 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot 뼈대 위에 문제 데이터(DB) + 모드 ①(미리 만든 문제 + 정답 공개)를 만들어, 닉네임만 정하면 바로 플레이 가능한 완성품을 만든다.

**Architecture:** Spring Boot(REST) + JPA(H2) 백엔드가 문제를 제공하고, 순수 HTML/JS 프론트가 문제 목록·플레이·정답 공개 UI를 그린다. 정답(solution)은 별도 엔드포인트로만 내려보내 브라우저에서 미리 엿볼 수 없게 한다. 닉네임은 서버 계정 없이 `localStorage`에만 둔다.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Web, Spring Data JPA, H2(로컬), JUnit 5, Gradle, 바닐라 JS

---

## File Structure

```
turtlesoup/
├─ build.gradle, settings.gradle, gradlew, gradle/wrapper/...   (Task 1 생성)
├─ src/main/java/com/turtlesoup/
│  ├─ TurtlesoupApplication.java                  앱 진입점 (Task 1)
│  └─ puzzle/
│     ├─ Difficulty.java                          난이도 enum (Task 2)
│     ├─ Puzzle.java                              JPA 엔티티 (Task 2)
│     ├─ PuzzleRepository.java                    JPA 리포지토리 (Task 3)
│     ├─ PuzzleSeeder.java                        시드 데이터 적재 (Task 4)
│     ├─ dto/PuzzleSummary.java                   목록용(정답 없음) (Task 5)
│     ├─ dto/PuzzlePlay.java                      플레이용(scenario, 정답 없음) (Task 5)
│     ├─ dto/PuzzleSolution.java                  정답 공개용 (Task 5)
│     ├─ PuzzleService.java                       조회 로직 (Task 6)
│     └─ PuzzleController.java                    REST API (Task 7)
├─ src/main/resources/
│  ├─ application.yml                             공통 설정 (Task 1)
│  ├─ application-local.yml                       H2 설정 (Task 1)
│  └─ static/
│     ├─ index.html                               닉네임 + 모드 선택 (Task 8)
│     ├─ classic.html                             클래식 플레이 (Task 9)
│     ├─ css/style.css                            공통 스타일 (Task 8)
│     └─ js/nickname.js, js/classic.js            (Task 8, 9)
└─ src/test/java/com/turtlesoup/puzzle/
   ├─ PuzzleRepositoryTest.java                   (Task 3)
   ├─ PuzzleSeederTest.java                       (Task 4)
   ├─ PuzzleServiceTest.java                      (Task 6)
   └─ PuzzleControllerTest.java                   (Task 7)
```

---

### Task 1: Spring Boot 프로젝트 스캐폴딩

**Files:**
- Create: `build.gradle`, `settings.gradle`, `gradlew`, `gradle/wrapper/*`, `src/main/java/com/turtlesoup/TurtlesoupApplication.java`, `src/main/resources/application.yml`, `src/main/resources/application-local.yml`

- [ ] **Step 1: Spring Initializr로 스켈레톤 생성**

`turtlesoup/` 폴더 안에서 실행 (이미 만들어둔 `docs/`는 건드리지 않음):

```bash
cd /Users/dasom/Desktop/진다솜/turtlesoup
curl -sS https://start.spring.io/starter.zip \
  -d type=gradle-project \
  -d language=java \
  -d javaVersion=21 \
  -d groupId=com.turtlesoup \
  -d artifactId=turtlesoup \
  -d name=turtlesoup \
  -d packageName=com.turtlesoup \
  -d dependencies=web,data-jpa,h2,validation \
  -o starter.zip
unzip -o starter.zip -d .
rm starter.zip
```

- [ ] **Step 2: 부팅 확인**

Run: `./gradlew bootRun`
Expected: 로그에 `Started TurtlesoupApplication` 출력. (DB 설정 전이라 JPA 경고가 보일 수 있음 — Step 3에서 해결.) `Ctrl+C`로 종료.

- [ ] **Step 3: application.yml / application-local.yml 작성**

`src/main/resources/application.yml` (Initializr가 만든 `application.properties`가 있으면 삭제하고 이 파일 사용):

```yaml
spring:
  profiles:
    active: local
server:
  port: 8080
```

`src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:turtlesoup;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  h2:
    console:
      enabled: true
      path: /h2-console
```

- [ ] **Step 4: 다시 부팅 확인**

Run: `./gradlew bootRun`
Expected: `Started TurtlesoupApplication`, 경고 없이 기동. 종료.

- [ ] **Step 5: 커밋**

```bash
cd /Users/dasom/Desktop/진다솜/turtlesoup
git init
printf '%s\n' '.gradle/' 'build/' '.idea/' '*.iml' '.DS_Store' > .gitignore
git add .
git commit -m "chore: scaffold Spring Boot project (web, jpa, h2)"
```

---

### Task 2: Difficulty enum + Puzzle 엔티티

**Files:**
- Create: `src/main/java/com/turtlesoup/puzzle/Difficulty.java`
- Create: `src/main/java/com/turtlesoup/puzzle/Puzzle.java`

- [ ] **Step 1: Difficulty enum 작성**

`src/main/java/com/turtlesoup/puzzle/Difficulty.java`:

```java
package com.turtlesoup.puzzle;

public enum Difficulty {
    EASY, NORMAL, HARD
}
```

- [ ] **Step 2: Puzzle 엔티티 작성**

`src/main/java/com/turtlesoup/puzzle/Puzzle.java`:

```java
package com.turtlesoup.puzzle;

import jakarta.persistence.*;

@Entity
@Table(name = "puzzle")
public class Puzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String scenario;

    @Column(nullable = false, length = 2000)
    private String solution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(length = 200)
    private String tags; // 쉼표 구분

    protected Puzzle() {
    }

    public Puzzle(String title, String scenario, String solution, Difficulty difficulty, String tags) {
        this.title = title;
        this.scenario = scenario;
        this.solution = solution;
        this.difficulty = difficulty;
        this.tags = tags;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getScenario() { return scenario; }
    public String getSolution() { return solution; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getTags() { return tags; }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/turtlesoup/puzzle/
git commit -m "feat: add Puzzle entity and Difficulty enum"
```

---

### Task 3: PuzzleRepository (TDD)

**Files:**
- Create: `src/main/java/com/turtlesoup/puzzle/PuzzleRepository.java`
- Test: `src/test/java/com/turtlesoup/puzzle/PuzzleRepositoryTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/turtlesoup/puzzle/PuzzleRepositoryTest.java`:

```java
package com.turtlesoup.puzzle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PuzzleRepositoryTest {

    @Autowired
    PuzzleRepository repository;

    @Test
    void savesAndFindsPuzzle() {
        Puzzle saved = repository.save(
            new Puzzle("제목", "상황", "정답", Difficulty.EASY, "고전"));

        assertThat(repository.findById(saved.getId())).isPresent();
        assertThat(repository.findById(saved.getId()).get().getScenario())
            .isEqualTo("상황");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests PuzzleRepositoryTest`
Expected: 컴파일 실패 — `PuzzleRepository` 타입이 없음.

- [ ] **Step 3: 리포지토리 작성**

`src/main/java/com/turtlesoup/puzzle/PuzzleRepository.java`:

```java
package com.turtlesoup.puzzle;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PuzzleRepository extends JpaRepository<Puzzle, Long> {
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests PuzzleRepositoryTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/turtlesoup/puzzle/PuzzleRepository.java src/test/java/com/turtlesoup/puzzle/PuzzleRepositoryTest.java
git commit -m "feat: add PuzzleRepository"
```

---

### Task 4: PuzzleSeeder — 시드 데이터 적재 (TDD)

**Files:**
- Create: `src/main/java/com/turtlesoup/puzzle/PuzzleSeeder.java`
- Test: `src/test/java/com/turtlesoup/puzzle/PuzzleSeederTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/turtlesoup/puzzle/PuzzleSeederTest.java`:

```java
package com.turtlesoup.puzzle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PuzzleSeederTest {

    @Autowired
    PuzzleRepository repository;

    @Test
    void seedsClassicPuzzlesOnStartup() {
        // 앱 컨텍스트가 뜰 때 PuzzleSeeder(CommandLineRunner)가 실행됨
        assertThat(repository.count()).isGreaterThanOrEqualTo(5);
        assertThat(repository.findAll())
            .anyMatch(p -> p.getTitle().contains("바다거북"));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests PuzzleSeederTest`
Expected: 실패 — `count()`가 0.

- [ ] **Step 3: 시더 작성**

`src/main/java/com/turtlesoup/puzzle/PuzzleSeeder.java`:

```java
package com.turtlesoup.puzzle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PuzzleSeeder implements CommandLineRunner {

    private final PuzzleRepository repository;

    public PuzzleSeeder(PuzzleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return; // 이미 적재됨 — 멱등
        }
        repository.saveAll(List.of(
            new Puzzle(
                "바다거북 스프",
                "한 남자가 식당에서 바다거북 스프를 주문해 한 입 먹고는, 집에 돌아가 스스로 목숨을 끊었다. 왜일까?",
                "과거 조난을 당했을 때, 동료가 '바다거북 스프'라며 건넨 고기를 먹고 살아남았다. 식당에서 진짜 바다거북 스프를 맛본 순간, 그때 먹은 것이 바다거북이 아니라 죽은 동료의 살이었음을 깨달았다.",
                Difficulty.HARD, "고전,충격"),
            new Puzzle(
                "엘리베이터 속 남자",
                "한 남자가 매일 아침 1층에서 엘리베이터를 타고 출근한다. 비가 오는 날이나 다른 사람과 함께 탈 때는 자기 집인 10층까지 가지만, 그렇지 않으면 7층에서 내려 계단으로 올라간다. 왜일까?",
                "남자는 키가 매우 작아서 10층 버튼에 손이 닿지 않는다. 우산이 있는 날엔 우산으로 누르고, 다른 사람이 있으면 대신 눌러달라 부탁한다. 평소엔 손이 닿는 7층까지만 누르고 나머지는 걸어 올라간다.",
                Difficulty.NORMAL, "고전,일상"),
            new Puzzle(
                "음악이 멈추자",
                "음악이 멈추자 한 여자가 죽었다. 무슨 일이 있었을까?",
                "여자는 줄타기 곡예사였다. 눈을 가린 채 공연했고, 음악이 멈추는 것이 줄의 끝(안전한 발판)에 도달했다는 신호였다. 그러나 그날 음악이 실수로 일찍 멈췄고, 여자는 아직 줄 위인데 끝인 줄 알고 발을 내디뎌 떨어졌다.",
                Difficulty.HARD, "고전"),
            new Puzzle(
                "사막의 시체",
                "사막 한가운데에 한 남자가 죽은 채 발견되었다. 그의 손에는 부러진 성냥개비가 쥐어져 있었다. 무슨 일이 있었을까?",
                "남자는 일행과 열기구를 타고 있었다. 기구가 추락하기 시작하자 무게를 줄이려 짐을 모두 버렸지만 역부족이었다. 결국 한 명이 뛰어내려야 했고, 성냥개비 제비뽑기에서 가장 짧은 것을 뽑은 그가 뛰어내렸다.",
                Difficulty.HARD, "고전"),
            new Puzzle(
                "두 잔의 물",
                "한 여자가 식당에서 물을 한 잔 마시고 안도하며 집으로 돌아갔다. 왜일까?",
                "여자는 심한 딸꾹질을 멈추려고 식당에 들어갔다. 종업원이 갑자기 총을 꺼내 그녀를 놀라게 했고, 딸꾹질이 멈췄다. 종업원은 처음부터 그녀를 도우려 일부러 놀래킨 것이었다. 여자는 고맙다는 인사 대신 물을 마시고 안도했다.",
                Difficulty.NORMAL, "고전,따뜻함"),
            new Puzzle(
                "초인종",
                "남자는 한밤중에 모르는 사람에게서 전화를 받았다. 아무 말도 오가지 않았지만, 전화를 끊은 뒤 그는 깊이 잠들 수 있었다. 왜일까?",
                "남자는 옆방의 코골이 때문에 잠들지 못하고 있었다. 그는 옆방으로 전화를 걸었고, 코를 골던 사람이 전화를 받으려 잠에서 깨어 코골이가 멈췄다. 남자는 아무 말 없이 전화를 끊고 그 사이에 잠들었다.",
                Difficulty.NORMAL, "일상,재치")
        ));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests PuzzleSeederTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/turtlesoup/puzzle/PuzzleSeeder.java src/test/java/com/turtlesoup/puzzle/PuzzleSeederTest.java
git commit -m "feat: seed classic turtle-soup puzzles on startup"
```

---

### Task 5: DTO 3종

**Files:**
- Create: `src/main/java/com/turtlesoup/puzzle/dto/PuzzleSummary.java`
- Create: `src/main/java/com/turtlesoup/puzzle/dto/PuzzlePlay.java`
- Create: `src/main/java/com/turtlesoup/puzzle/dto/PuzzleSolution.java`

- [ ] **Step 1: PuzzleSummary 작성 (목록용 — 정답·상황 없음)**

`src/main/java/com/turtlesoup/puzzle/dto/PuzzleSummary.java`:

```java
package com.turtlesoup.puzzle.dto;

import com.turtlesoup.puzzle.Difficulty;
import com.turtlesoup.puzzle.Puzzle;

public record PuzzleSummary(Long id, String title, Difficulty difficulty, String tags) {
    public static PuzzleSummary from(Puzzle p) {
        return new PuzzleSummary(p.getId(), p.getTitle(), p.getDifficulty(), p.getTags());
    }
}
```

- [ ] **Step 2: PuzzlePlay 작성 (플레이용 — scenario 포함, 정답 제외)**

`src/main/java/com/turtlesoup/puzzle/dto/PuzzlePlay.java`:

```java
package com.turtlesoup.puzzle.dto;

import com.turtlesoup.puzzle.Difficulty;
import com.turtlesoup.puzzle.Puzzle;

public record PuzzlePlay(Long id, String title, String scenario, Difficulty difficulty, String tags) {
    public static PuzzlePlay from(Puzzle p) {
        return new PuzzlePlay(p.getId(), p.getTitle(), p.getScenario(), p.getDifficulty(), p.getTags());
    }
}
```

- [ ] **Step 3: PuzzleSolution 작성 (정답 공개용)**

`src/main/java/com/turtlesoup/puzzle/dto/PuzzleSolution.java`:

```java
package com.turtlesoup.puzzle.dto;

import com.turtlesoup.puzzle.Puzzle;

public record PuzzleSolution(Long id, String solution) {
    public static PuzzleSolution from(Puzzle p) {
        return new PuzzleSolution(p.getId(), p.getSolution());
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/turtlesoup/puzzle/dto/
git commit -m "feat: add puzzle DTOs (summary/play/solution)"
```

---

### Task 6: PuzzleService (TDD)

**Files:**
- Create: `src/main/java/com/turtlesoup/puzzle/PuzzleService.java`
- Test: `src/test/java/com/turtlesoup/puzzle/PuzzleServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/turtlesoup/puzzle/PuzzleServiceTest.java`:

```java
package com.turtlesoup.puzzle;

import com.turtlesoup.puzzle.dto.PuzzlePlay;
import com.turtlesoup.puzzle.dto.PuzzleSolution;
import com.turtlesoup.puzzle.dto.PuzzleSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PuzzleServiceTest {

    @Mock
    PuzzleRepository repository;

    @InjectMocks
    PuzzleService service;

    private Puzzle samplePuzzle() {
        return new Puzzle("바다거북 스프", "상황 텍스트", "정답 텍스트", Difficulty.HARD, "고전");
    }

    @Test
    void listReturnsSummariesWithoutSolution() {
        when(repository.findAll()).thenReturn(List.of(samplePuzzle()));

        List<PuzzleSummary> result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("바다거북 스프");
    }

    @Test
    void getForPlayReturnsScenarioButNoSolution() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePuzzle()));

        PuzzlePlay result = service.getForPlay(1L);

        assertThat(result.scenario()).isEqualTo("상황 텍스트");
    }

    @Test
    void getForPlayThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForPlay(99L))
            .isInstanceOf(PuzzleNotFoundException.class);
    }

    @Test
    void getSolutionReturnsSolution() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePuzzle()));

        PuzzleSolution result = service.getSolution(1L);

        assertThat(result.solution()).isEqualTo("정답 텍스트");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests PuzzleServiceTest`
Expected: 컴파일 실패 — `PuzzleService`, `PuzzleNotFoundException` 없음.

- [ ] **Step 3: 예외 클래스 작성**

`src/main/java/com/turtlesoup/puzzle/PuzzleNotFoundException.java`:

```java
package com.turtlesoup.puzzle;

public class PuzzleNotFoundException extends RuntimeException {
    public PuzzleNotFoundException(Long id) {
        super("문제를 찾을 수 없습니다: " + id);
    }
}
```

- [ ] **Step 4: 서비스 작성**

`src/main/java/com/turtlesoup/puzzle/PuzzleService.java`:

```java
package com.turtlesoup.puzzle;

import com.turtlesoup.puzzle.dto.PuzzlePlay;
import com.turtlesoup.puzzle.dto.PuzzleSolution;
import com.turtlesoup.puzzle.dto.PuzzleSummary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PuzzleService {

    private final PuzzleRepository repository;

    public PuzzleService(PuzzleRepository repository) {
        this.repository = repository;
    }

    public List<PuzzleSummary> list() {
        return repository.findAll().stream().map(PuzzleSummary::from).toList();
    }

    public PuzzlePlay getForPlay(Long id) {
        return PuzzlePlay.from(find(id));
    }

    public PuzzleSolution getSolution(Long id) {
        return PuzzleSolution.from(find(id));
    }

    private Puzzle find(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new PuzzleNotFoundException(id));
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests PuzzleServiceTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/turtlesoup/puzzle/PuzzleService.java src/main/java/com/turtlesoup/puzzle/PuzzleNotFoundException.java src/test/java/com/turtlesoup/puzzle/PuzzleServiceTest.java
git commit -m "feat: add PuzzleService with play/solution separation"
```

---

### Task 7: PuzzleController + 예외 핸들링 (TDD)

**Files:**
- Create: `src/main/java/com/turtlesoup/puzzle/PuzzleController.java`
- Test: `src/test/java/com/turtlesoup/puzzle/PuzzleControllerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/turtlesoup/puzzle/PuzzleControllerTest.java`:

```java
package com.turtlesoup.puzzle;

import com.turtlesoup.puzzle.dto.PuzzlePlay;
import com.turtlesoup.puzzle.dto.PuzzleSolution;
import com.turtlesoup.puzzle.dto.PuzzleSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PuzzleController.class)
class PuzzleControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PuzzleService service;

    @Test
    void listEndpointReturnsSummaries() throws Exception {
        when(service.list()).thenReturn(
            List.of(new PuzzleSummary(1L, "바다거북 스프", Difficulty.HARD, "고전")));

        mockMvc.perform(get("/api/puzzles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("바다거북 스프"));
    }

    @Test
    void playEndpointReturnsScenario() throws Exception {
        when(service.getForPlay(1L)).thenReturn(
            new PuzzlePlay(1L, "바다거북 스프", "상황 텍스트", Difficulty.HARD, "고전"));

        mockMvc.perform(get("/api/puzzles/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scenario").value("상황 텍스트"));
    }

    @Test
    void solutionEndpointReturnsSolution() throws Exception {
        when(service.getSolution(1L)).thenReturn(new PuzzleSolution(1L, "정답 텍스트"));

        mockMvc.perform(get("/api/puzzles/1/solution"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solution").value("정답 텍스트"));
    }

    @Test
    void missingPuzzleReturns404() throws Exception {
        when(service.getForPlay(99L)).thenThrow(new PuzzleNotFoundException(99L));

        mockMvc.perform(get("/api/puzzles/99"))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests PuzzleControllerTest`
Expected: 컴파일 실패 — `PuzzleController` 없음.

- [ ] **Step 3: 컨트롤러 작성**

`src/main/java/com/turtlesoup/puzzle/PuzzleController.java`:

```java
package com.turtlesoup.puzzle;

import com.turtlesoup.puzzle.dto.PuzzlePlay;
import com.turtlesoup.puzzle.dto.PuzzleSolution;
import com.turtlesoup.puzzle.dto.PuzzleSummary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {

    private final PuzzleService service;

    public PuzzleController(PuzzleService service) {
        this.service = service;
    }

    @GetMapping
    public List<PuzzleSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PuzzlePlay play(@PathVariable Long id) {
        return service.getForPlay(id);
    }

    @GetMapping("/{id}/solution")
    public PuzzleSolution solution(@PathVariable Long id) {
        return service.getSolution(id);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PuzzleNotFoundException.class)
    public void handleNotFound() {
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests PuzzleControllerTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 모든 테스트 통과.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/turtlesoup/puzzle/PuzzleController.java src/test/java/com/turtlesoup/puzzle/PuzzleControllerTest.java
git commit -m "feat: add PuzzleController REST endpoints"
```

---

### Task 8: 프론트 — 닉네임 입력 + 모드 선택 페이지

**Files:**
- Create: `src/main/resources/static/index.html`
- Create: `src/main/resources/static/css/style.css`
- Create: `src/main/resources/static/js/nickname.js`

- [ ] **Step 1: 공통 스타일 작성**

`src/main/resources/static/css/style.css`:

```css
* { box-sizing: border-box; }
body {
  font-family: system-ui, "Apple SD Gothic Neo", sans-serif;
  max-width: 720px;
  margin: 0 auto;
  padding: 24px;
  background: #f7f6f2;
  color: #222;
}
h1 { font-size: 1.6rem; }
button {
  font-size: 1rem;
  padding: 10px 16px;
  border: none;
  border-radius: 8px;
  background: #2e7d6b;
  color: #fff;
  cursor: pointer;
}
button:hover { background: #275f53; }
.card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin: 12px 0;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.muted { color: #777; font-size: 0.9rem; }
input[type=text] {
  font-size: 1rem;
  padding: 8px;
  border: 1px solid #ccc;
  border-radius: 8px;
  width: 60%;
}
.hidden { display: none; }
```

- [ ] **Step 2: 닉네임 로직 작성**

`src/main/resources/static/js/nickname.js`:

```javascript
// 닉네임은 localStorage에만 저장한다 (서버 계정 없음).
const NICK_KEY = "turtlesoup.nickname";

function getNickname() {
  return localStorage.getItem(NICK_KEY);
}

function setNickname(name) {
  localStorage.setItem(NICK_KEY, name);
}
```

- [ ] **Step 3: 메인 페이지 작성**

`src/main/resources/static/index.html`:

```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>바다거북스프</title>
  <link rel="stylesheet" href="/css/style.css">
</head>
<body>
  <h1>🐢 바다거북스프</h1>

  <div id="nickname-setup" class="card hidden">
    <p>닉네임을 정하면 바로 시작할 수 있어요. (가입 없음)</p>
    <input type="text" id="nickname-input" placeholder="닉네임" maxlength="20">
    <button id="nickname-save">시작</button>
  </div>

  <div id="mode-select" class="card hidden">
    <p><b id="hello"></b> 님, 모드를 골라주세요.</p>
    <p><button onclick="location.href='/classic.html'">① 미리 만든 문제 풀기</button></p>
    <p class="muted">② AI 출제자, ③ 멀티플레이는 곧 추가됩니다.</p>
    <p><button onclick="changeNickname()" style="background:#888">닉네임 변경</button></p>
  </div>

  <script src="/js/nickname.js"></script>
  <script>
    const setup = document.getElementById("nickname-setup");
    const select = document.getElementById("mode-select");

    function render() {
      const nick = getNickname();
      if (nick) {
        document.getElementById("hello").textContent = nick;
        select.classList.remove("hidden");
        setup.classList.add("hidden");
      } else {
        setup.classList.remove("hidden");
        select.classList.add("hidden");
      }
    }

    function changeNickname() {
      localStorage.removeItem("turtlesoup.nickname");
      render();
    }

    document.getElementById("nickname-save").addEventListener("click", () => {
      const v = document.getElementById("nickname-input").value.trim();
      if (v) { setNickname(v); render(); }
    });

    render();
  </script>
</body>
</html>
```

- [ ] **Step 4: 수동 확인**

Run: `./gradlew bootRun`
브라우저에서 `http://localhost:8080/` 접속.
Expected: 닉네임 입력칸 표시 → 닉네임 입력 후 "시작" → 모드 선택 화면, "① 미리 만든 문제 풀기" 버튼 보임. 새로고침해도 닉네임 유지. "닉네임 변경" 누르면 다시 입력칸. 확인 후 종료.

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/static/index.html src/main/resources/static/css/style.css src/main/resources/static/js/nickname.js
git commit -m "feat: add nickname entry and mode-select page"
```

---

### Task 9: 프론트 — 클래식 모드 플레이 페이지

**Files:**
- Create: `src/main/resources/static/classic.html`
- Create: `src/main/resources/static/js/classic.js`

- [ ] **Step 1: 클래식 페이지 작성**

`src/main/resources/static/classic.html`:

```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>바다거북스프 — 문제 풀기</title>
  <link rel="stylesheet" href="/css/style.css">
</head>
<body>
  <h1>🐢 문제 풀기</h1>
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
    <button id="reveal-btn">정답 보기</button>
    <div id="solution-card" class="card hidden">
      <h3>정답</h3>
      <p id="play-solution"></p>
    </div>
    <p><button onclick="showList()" style="background:#888">← 목록으로</button></p>
  </div>

  <script src="/js/classic.js"></script>
</body>
</html>
```

- [ ] **Step 2: 클래식 로직 작성**

`src/main/resources/static/js/classic.js`:

```javascript
const listView = document.getElementById("list-view");
const playView = document.getElementById("play-view");

async function loadList() {
  const res = await fetch("/api/puzzles");
  const puzzles = await res.json();
  const container = document.getElementById("puzzle-list");
  container.innerHTML = "";
  puzzles.forEach(p => {
    const div = document.createElement("div");
    div.className = "card";
    div.innerHTML =
      `<b>${p.title}</b> <span class="muted">[${p.difficulty}]</span>`;
    const btn = document.createElement("button");
    btn.textContent = "풀기";
    btn.style.marginTop = "8px";
    btn.style.display = "block";
    btn.addEventListener("click", () => openPuzzle(p.id));
    div.appendChild(btn);
    container.appendChild(div);
  });
}

async function openPuzzle(id) {
  const res = await fetch(`/api/puzzles/${id}`);
  const p = await res.json();
  document.getElementById("play-title").textContent = p.title;
  document.getElementById("play-meta").textContent = `난이도: ${p.difficulty}`;
  document.getElementById("play-scenario").textContent = p.scenario;

  const solutionCard = document.getElementById("solution-card");
  solutionCard.classList.add("hidden");
  document.getElementById("play-solution").textContent = "";

  const revealBtn = document.getElementById("reveal-btn");
  revealBtn.classList.remove("hidden");
  revealBtn.onclick = async () => {
    const r = await fetch(`/api/puzzles/${id}/solution`);
    const s = await r.json();
    document.getElementById("play-solution").textContent = s.solution;
    solutionCard.classList.remove("hidden");
    revealBtn.classList.add("hidden");
  };

  listView.classList.add("hidden");
  playView.classList.remove("hidden");
}

function showList() {
  playView.classList.add("hidden");
  listView.classList.remove("hidden");
}

loadList();
```

- [ ] **Step 3: 수동 확인**

Run: `./gradlew bootRun`
브라우저에서 `http://localhost:8080/classic.html` 접속.
Expected:
- 문제 목록(바다거북 스프 등)이 보인다.
- "풀기" 클릭 → 상황(scenario) 표시, 정답은 안 보임.
- "정답 보기" 클릭 → 정답(solution) 표시, 버튼 사라짐.
- 개발자도구 Network 탭에서 `/api/puzzles/{id}` 응답에 `solution` 필드가 **없음**을 확인 (정답은 `/solution` 호출 시에만 내려옴).
- "목록으로" 동작 확인. 확인 후 종료.

- [ ] **Step 4: 커밋**

```bash
git add src/main/resources/static/classic.html src/main/resources/static/js/classic.js
git commit -m "feat: add classic mode play page with solution reveal"
```

---

### Task 10: 전체 통합 점검

- [ ] **Step 1: 전체 테스트**

Run: `./gradlew test`
Expected: 모든 테스트 통과, `BUILD SUCCESSFUL`.

- [ ] **Step 2: 엔드투엔드 수동 점검**

Run: `./gradlew bootRun`
시나리오: `http://localhost:8080/` → 닉네임 입력 → "① 미리 만든 문제 풀기" → 문제 선택 → 상황 읽기 → 정답 보기.
Expected: 끊김 없이 동작.

- [ ] **Step 3: 최종 커밋 (필요 시)**

```bash
git add -A
git commit -m "chore: foundation + classic mode complete" || echo "변경 없음"
```

---

## 다음 플랜 (이 플랜 범위 밖)

- **플랜 2 — ② AI 출제자**: Spring AI 의존성, `ChatClient`(local Ollama / prod Gemini), `AiJudgeService`, 채팅 UI, `play_history` 기록
- **플랜 3 — ③ 멀티플레이**: Spring WebSocket(STOMP), `RoomService`(메모리), 방 생성/입장/실시간 채팅
- **플랜 4 — 배포**: Dockerfile, Render/Railway, Supabase Postgres(prod 프로파일), Gemini 키
