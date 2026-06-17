# 바다거북스프 — 클래식 힌트 (플랜 4a)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox(`- [ ]`).

**Goal:** ① 클래식 모드에서 문제마다 사전 작성된 점진적 힌트 3개를 "힌트 (n/3)" 버튼으로 하나씩 볼 수 있게 한다.

**Architecture:** `Puzzle`에 hint1/2/3(nullable) 추가. 힌트는 한 번에 하나씩 전용 엔드포인트 `GET /api/puzzles/{id}/hint/{n}`로만 내려가 미리보기 불가(정답 누출 방지 설계 유지). 오프라인 동작(LLM 불필요). AI 모드 힌트(LLM 생성)는 별도 후속.

**Tech Stack:** Boot 3.5.15, JPA, JUnit, 바닐라 JS

---

### Task 1: Puzzle 힌트 필드 + 26문제 힌트 작성 (모델 + 콘텐츠)

**Files:** `puzzle/Puzzle.java`, `puzzle/PuzzleSeeder.java`, test `puzzle/PuzzleSeederTest.java`

- [ ] **Step 1: Puzzle에 hint1/2/3 + 8-인자 생성자 + getHint(n) 추가**

기존 5-인자 생성자는 그대로 둔다(다른 테스트가 사용). 추가:
```java
    @Column(length = 1000) private String hint1;
    @Column(length = 1000) private String hint2;
    @Column(length = 1000) private String hint3;

    public Puzzle(String title, String scenario, String solution, Difficulty difficulty, String tags,
                  String hint1, String hint2, String hint3) {
        this(title, scenario, solution, difficulty, tags);
        this.hint1 = hint1;
        this.hint2 = hint2;
        this.hint3 = hint3;
    }

    public String getHint1() { return hint1; }
    public String getHint2() { return hint2; }
    public String getHint3() { return hint3; }
    public String getHint(int n) {
        return switch (n) { case 1 -> hint1; case 2 -> hint2; case 3 -> hint3; default -> null; };
    }
```
(기존 5-인자 생성자에 `this(...)` 위임이 가능하도록, 5-인자 생성자는 유지하고 8-인자가 그것을 호출.)

- [ ] **Step 2: 시더의 26문제를 8-인자(힌트 3개 포함)로 재작성**

PuzzleSeeder.java의 각 `new Puzzle(title, scenario, solution, Difficulty.X, tags)`를 `new Puzzle(title, scenario, solution, Difficulty.X, tags, hint1, hint2, hint3)`로 바꾼다. 각 문제의 scenario·solution을 읽고 **점진적 힌트 3개**를 작성:
- hint1 = 막연한 방향(주제/관점 환기), hint2 = 좀 더 구체적, hint3 = 정답에 꽤 근접(단, 정답 문장을 그대로 노출하지 말 것).
- 한국어, 각 1~2문장. 정답을 직접 베끼지 말고 "추론을 돕는" 수준으로.
- 자바 문자열 내 큰따옴표는 `\"`로 이스케이프.

- [ ] **Step 3: 시더 테스트에 힌트 단언 추가**

`PuzzleSeederTest`에 (기존 count 26 단언 유지) 추가:
```java
    @Test
    void seededPuzzlesHaveThreeHints() {
        Puzzle p = repository.findAll().stream()
            .filter(x -> x.getTitle().contains("바다거북")).findFirst().orElseThrow();
        assertThat(p.getHint(1)).isNotBlank();
        assertThat(p.getHint(2)).isNotBlank();
        assertThat(p.getHint(3)).isNotBlank();
    }
```

- [ ] **Step 4: 테스트 + 커밋**

`./gradlew test` → green.
```bash
git add src/main/java/com/turtlesoup/puzzle/Puzzle.java src/main/java/com/turtlesoup/puzzle/PuzzleSeeder.java src/test/java/com/turtlesoup/puzzle/PuzzleSeederTest.java
git commit -m "feat: add 3 progressive hints to each puzzle"
```

---

### Task 2: 힌트 엔드포인트 (TDD)

**Files:** `puzzle/dto/PuzzleHint.java`, `puzzle/PuzzleService.java`(메서드 추가), `puzzle/PuzzleController.java`(엔드포인트 추가), tests

- [ ] **Step 1: DTO**
```java
package com.turtlesoup.puzzle.dto;
public record PuzzleHint(int n, String hint) {}
```

- [ ] **Step 2: 실패 테스트 추가 (PuzzleServiceTest)**
```java
    @Test
    void getHintReturnsNthHint() {
        Puzzle p = new Puzzle("t","s","sol",Difficulty.EASY,"고전","힌트1","힌트2","힌트3");
        when(repository.findById(1L)).thenReturn(Optional.of(p));
        assertThat(service.getHint(1L, 2).hint()).isEqualTo("힌트2");
    }
    @Test
    void getHintThrowsWhenMissing() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHint(9L, 1)).isInstanceOf(PuzzleNotFoundException.class);
    }
```
(상단 import에 `com.turtlesoup.puzzle.dto.PuzzleHint` 추가)

- [ ] **Step 3: PuzzleService.getHint 구현**
```java
    public PuzzleHint getHint(Long id, int n) {
        return new PuzzleHint(n, find(id).getHint(n));
    }
```
(`find`는 기존 private 메서드 — PuzzleNotFoundException 던짐)

- [ ] **Step 4: 컨트롤러 테스트 추가 (PuzzleControllerTest)**
```java
    @Test
    void hintEndpointReturnsHint() throws Exception {
        when(service.getHint(1L, 2)).thenReturn(new com.turtlesoup.puzzle.dto.PuzzleHint(2, "두번째힌트"));
        mockMvc.perform(get("/api/puzzles/1/hint/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hint").value("두번째힌트"));
    }
```

- [ ] **Step 5: 컨트롤러 엔드포인트 추가**
```java
    @GetMapping("/{id}/hint/{n}")
    public com.turtlesoup.puzzle.dto.PuzzleHint hint(@PathVariable Long id, @PathVariable int n) {
        return service.getHint(id, n);
    }
```

- [ ] **Step 6: 테스트 + 커밋**

`./gradlew test` → green.
```bash
git add src/main/java/com/turtlesoup/puzzle/dto/PuzzleHint.java src/main/java/com/turtlesoup/puzzle/PuzzleService.java src/main/java/com/turtlesoup/puzzle/PuzzleController.java src/test/java/com/turtlesoup/puzzle/PuzzleServiceTest.java src/test/java/com/turtlesoup/puzzle/PuzzleControllerTest.java
git commit -m "feat: add GET /api/puzzles/{id}/hint/{n} endpoint"
```

---

### Task 3: 클래식 UI 힌트 버튼 (n/3)

**Files:** `static/classic.html`, `static/js/classic.js`

- [ ] **Step 1: classic.html play-view에 힌트 영역 추가**

`reveal-btn` 버튼 줄 근처(정답 보기 위)에 추가:
```html
            <div style="margin:10px 0;">
              <button id="hint-btn">힌트 보기 (<span id="hint-n">0</span>/3)</button>
            </div>
            <div id="hint-list"></div>
```

- [ ] **Step 2: classic.js — 힌트 로직**

`openPuzzle(id)` 안 상태 초기화에 추가: `hintsUsed = 0; document.getElementById("hint-n").textContent="0"; document.getElementById("hint-list").textContent=""; document.getElementById("hint-btn").disabled=false;`
(파일 상단에 `let hintsUsed = 0;` 선언, `let currentPuzzleId` 등과 함께. openPuzzle은 currentId를 알아야 하므로 `currentPuzzleId = id;` 보관.)

힌트 버튼 핸들러(파일 하단, openPuzzle에서 onclick 바인딩 또는 전역 리스너):
```javascript
async function useHint() {
  if (hintsUsed >= 3) return;
  const next = hintsUsed + 1;
  const res = await fetch(`/api/puzzles/${currentPuzzleId}/hint/${next}`);
  if (!res.ok) { alert("힌트를 불러오지 못했습니다."); return; }
  const data = await res.json();
  if (!data.hint) { alert("이 문제에는 더 이상 힌트가 없습니다."); document.getElementById("hint-btn").disabled = true; return; }
  hintsUsed = next;
  const div = document.createElement("div");
  div.className = "card";
  div.style.background = "var(--warn-bg)"; div.style.color = "var(--warn)";
  const b = document.createElement("b"); b.textContent = `힌트 ${next}/3`;
  const p = document.createElement("p"); p.style.margin = "4px 0 0"; p.textContent = data.hint;
  div.append(b, p);
  document.getElementById("hint-list").appendChild(div);
  document.getElementById("hint-n").textContent = String(hintsUsed);
  if (hintsUsed >= 3) document.getElementById("hint-btn").disabled = true;
}
document.getElementById("hint-btn").addEventListener("click", useHint);
```
(`currentPuzzleId`를 openPuzzle 시작에서 설정하도록 보장.)

- [ ] **Step 3: 문법 + 수동 확인 + 커밋**

`node --check src/main/resources/static/js/classic.js`
앱 띄워 클래식 문제 열고 "힌트 보기" 3번 → 힌트 누적·3회 후 비활성화, `/api/puzzles/{id}` 응답엔 여전히 hint 없음(엔드포인트로만).
```bash
git add src/main/resources/static/classic.html src/main/resources/static/js/classic.js
git commit -m "feat: classic mode hint button (up to 3 per puzzle)"
```

---

### Task 4: 통합 점검
- [ ] `./gradlew test` green.
- [ ] 앱 기동 → `GET /api/puzzles/1/hint/1..3` 응답 확인, `/api/puzzles/1`엔 hint 없음 확인, 클래식 화면 힌트 3회 동작.

## 다음
- **② AI 힌트**: `AiJudgeService.hint(puzzleId)` LLM 비누설 힌트 + `POST /api/ai/{id}/hint` + ai.js 힌트 버튼.
- **배포**(마지막).
