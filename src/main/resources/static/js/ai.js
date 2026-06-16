const listView = document.getElementById("list-view");
const playView = document.getElementById("play-view");

let currentPuzzleId = null;
let questionCount = 0;
let solved = false;
let asking = false; // 요청 진행 중 중복 제출 방지

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
  if (solved || asking) return;
  const input = document.getElementById("question-input");
  const askBtn = document.getElementById("ask-btn");
  const q = input.value.trim();
  if (!q) return;

  asking = true;
  askBtn.disabled = true;
  appendChat("나", q);
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
      appendChat("AI", "답변을 가져오지 못했습니다. (Ollama가 켜져 있는지 확인하세요)");
      return;
    }
    const data = await res.json();
    appendChat("AI", VERDICT_LABEL[data.verdict] || data.verdict);

    if (data.verdict === "CORRECT") {
      solved = true;
      input.disabled = true;
      recordSolve();
    }
  } catch (e) {
    appendChat("AI", "서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  } finally {
    asking = false;
    if (!solved) askBtn.disabled = false; // 정답 전이면 다시 질문 가능
  }
}

async function recordSolve() {
  const nickname = (typeof getNickname === "function" && getNickname()) || "익명";
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
document.getElementById("question-input").addEventListener("keydown", e => {
  if (e.key === "Enter") ask();
});

loadList();
