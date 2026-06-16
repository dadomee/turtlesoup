const listView = document.getElementById("list-view");
const playView = document.getElementById("play-view");

let currentPuzzleId = null;
let questionCount = 0;
let solved = false;
let asking = false; // 요청 진행 중 중복 제출 방지
let hintsUsed = 0;

const VERDICT_LABEL = {
  YES: "예",
  NO: "아니오",
  IRRELEVANT: "상관없음",
  CORRECT: "정답!",
  UNKNOWN: "잘 모르겠어요 — 다르게 물어봐 주세요"
};

function meName() {
  return (typeof getNickname === "function" && getNickname()) || "나";
}

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
    div.className = "card clickable";
    div.addEventListener("click", () => openPuzzle(p.id));
    const title = document.createElement("h3");
    title.textContent = p.title;
    const meta = document.createElement("span");
    meta.className = "badge diff";
    meta.textContent = p.difficulty;
    div.append(title, meta);
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
  const input = document.getElementById("question-input");
  input.value = "";
  input.disabled = false;
  document.getElementById("ask-btn").disabled = false;
  hintsUsed = 0;
  document.getElementById("hint-n").textContent = "0";
  document.getElementById("hint-btn").disabled = false;

  document.getElementById("play-title").textContent = p.title;
  document.getElementById("play-meta").textContent = `난이도: ${p.difficulty}`;
  document.getElementById("play-scenario").textContent = p.scenario;

  appendBotText("이 사건의 진상을 추리해 보세요. 예/아니오로 답할 수 있는 질문을 던지면 됩니다.");

  listView.classList.add("hidden");
  playView.classList.remove("hidden");
}

function msgRow(side, name, avatarText, contentEl) {
  const row = document.createElement("div");
  row.className = "msg";
  const av = document.createElement("div");
  av.className = "msg-avatar " + side;
  av.textContent = avatarText;
  const body = document.createElement("div");
  const nm = document.createElement("div");
  nm.className = "msg-name";
  nm.textContent = name;
  body.appendChild(nm);
  body.appendChild(contentEl);
  row.append(av, body);
  const log = document.getElementById("chat-log");
  log.appendChild(row);
  const content = document.querySelector(".content");
  if (content) content.scrollTop = content.scrollHeight;
}

function textNode(t) {
  const d = document.createElement("div");
  d.className = "msg-text";
  d.textContent = t;
  return d;
}

function appendMyMsg(text) {
  const me = meName();
  msgRow("me", me, me.charAt(0).toUpperCase(), textNode(text));
}

function appendBotText(text) {
  msgRow("bot", "추리비서 AI", "AI", textNode(text));
}

function appendBotVerdict(verdict) {
  const pill = document.createElement("span");
  pill.className = "verdict " + verdict.toLowerCase();
  pill.textContent = VERDICT_LABEL[verdict] || verdict;
  msgRow("bot", "추리비서 AI", "AI", pill);
}

async function ask() {
  if (solved || asking) return;
  const input = document.getElementById("question-input");
  const askBtn = document.getElementById("ask-btn");
  const q = input.value.trim();
  if (!q) return;

  asking = true;
  askBtn.disabled = true;
  appendMyMsg(q);
  input.value = "";
  questionCount += 1;
  document.getElementById("q-count").textContent = String(questionCount);

  try {
    const res = await fetch(`/api/ai/${currentPuzzleId}/ask`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: q })
    });
    if (!res.ok) {
      appendBotText("답변을 가져오지 못했습니다. (Ollama가 켜져 있는지 확인하세요)");
      return;
    }
    const data = await res.json();
    appendBotVerdict(data.verdict);

    if (data.verdict === "CORRECT") {
      solved = true;
      input.disabled = true;
      document.getElementById("hint-btn").disabled = true;
      appendBotText(`정답입니다! ${questionCount}번 만에 맞히셨어요. 🎉`);
      await revealSolution();
      recordSolve();
    }
  } catch (e) {
    appendBotText("서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  } finally {
    asking = false;
    if (!solved) askBtn.disabled = false;
  }
}

async function revealSolution() {
  try {
    const r = await fetch(`/api/puzzles/${currentPuzzleId}/solution`);
    if (!r.ok) return;
    const s = await r.json();
    appendBotSolution(s.solution);
  } catch (e) {
    // 해설 노출 실패는 게임 진행에 치명적이지 않으므로 조용히 무시
  }
}

function appendBotSolution(text) {
  const box = document.createElement("div");
  box.className = "msg-text";
  const label = document.createElement("div");
  label.style.fontWeight = "500";
  label.style.marginBottom = "5px";
  label.textContent = "📖 전체 해설";
  const body = document.createElement("div");
  body.style.background = "var(--info-bg)";
  body.style.borderRadius = "8px";
  body.style.padding = "10px 12px";
  body.style.lineHeight = "1.7";
  body.textContent = text;
  box.append(label, body);
  msgRow("bot", "추리비서 AI", "AI", box);
}

function appendBotHint(text, n) {
  const box = document.createElement("div");
  box.className = "msg-text";
  const label = document.createElement("div");
  label.style.fontWeight = "500";
  label.style.marginBottom = "4px";
  label.textContent = `💡 힌트 ${n}/3`;
  const body = document.createElement("div");
  body.style.background = "var(--warn-bg)";
  body.style.color = "var(--warn)";
  body.style.borderRadius = "8px";
  body.style.padding = "10px 12px";
  body.style.lineHeight = "1.7";
  body.textContent = text;
  box.append(label, body);
  msgRow("bot", "추리비서 AI", "AI", box);
}

async function useHint() {
  if (solved || hintsUsed >= 3) return;
  const hintBtn = document.getElementById("hint-btn");
  const next = hintsUsed + 1;
  hintBtn.disabled = true;
  try {
    const res = await fetch(`/api/ai/${currentPuzzleId}/hint/${next}`, { method: "POST" });
    if (!res.ok) { appendBotText("힌트를 가져오지 못했습니다. (Ollama가 켜져 있는지 확인하세요)"); return; }
    const data = await res.json();
    hintsUsed = next;
    appendBotHint(data.hint, next);
    document.getElementById("hint-n").textContent = String(hintsUsed);
  } catch (e) {
    appendBotText("힌트 요청 중 오류가 발생했습니다.");
  } finally {
    if (!solved && hintsUsed < 3) hintBtn.disabled = false;
  }
}

async function recordSolve() {
  const nickname = meName();
  try {
    const res = await fetch(`/api/ai/${currentPuzzleId}/solve`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ nickname, questionCount })
    });
    if (!res.ok) console.warn("기록 저장 실패:", res.status);
  } catch (e) {
    console.warn("기록 저장 중 오류:", e);
  }
}

function showList() {
  playView.classList.add("hidden");
  listView.classList.remove("hidden");
}

document.getElementById("ask-btn").addEventListener("click", ask);
document.getElementById("hint-btn").addEventListener("click", useHint);
document.getElementById("question-input").addEventListener("keydown", e => {
  if (e.key === "Enter") ask();
});

loadList();
