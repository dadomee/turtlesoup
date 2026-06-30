const listView = document.getElementById("list-view");
const playView = document.getElementById("play-view");

let currentPuzzleId = null;
let questionCount = 0;
let solved = false;
let asking = false; // 요청 진행 중 중복 제출 방지
let hintsUsed = 0;

// 대화 저장: 문제별 localStorage (목록 갔다 와도/새로고침해도 복원)
let convoKey = null;
let convo = null; // { entries:[...], questionCount, hintsUsed, solved }
let aborter = null; // 진행 중인 AI 요청 취소용 (문제 나가기/전환 시)

const VERDICT_LABEL = {
  YES: "예",
  NO: "아니오",
  IRRELEVANT: "상관없음",
  CORRECT: "정답!",
  UNKNOWN: "🤔 질문이 모호해요 — 더 구체적으로 물어봐 주세요"
};

function meName() {
  return (typeof getNickname === "function" && getNickname()) || "나";
}

function loadConvo(id) {
  try { return JSON.parse(localStorage.getItem("turtlesoup.ai." + id)); } catch (e) { return null; }
}
function saveConvo() {
  if (!convoKey || !convo) return;
  convo.questionCount = questionCount;
  convo.hintsUsed = hintsUsed;
  convo.solved = solved;
  try { localStorage.setItem(convoKey, JSON.stringify(convo)); } catch (e) {}
}
function renderEntry(en) {
  if (en.kind === "my") renderMy(en.text);
  else if (en.kind === "botText") renderBotText(en.text);
  else if (en.kind === "verdict") renderBotVerdict(en.verdict);
  else if (en.kind === "solution") renderBotSolution(en.text);
  else if (en.kind === "hint") renderBotHint(en.text, en.n);
}
function pushEntry(en) {
  if (!convo) convo = { entries: [] };
  convo.entries.push(en);
  renderEntry(en);
  saveConvo();
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
  if (aborter) { aborter.abort(); aborter = null; }   // 이전 문제의 미완료 요청 취소
  const res = await fetch(`/api/puzzles/${id}`);
  if (!res.ok) { alert("문제를 불러오지 못했습니다."); return; }
  const p = await res.json();

  currentPuzzleId = id;
  convoKey = "turtlesoup.ai." + id;
  document.getElementById("chat-log").textContent = "";

  document.getElementById("play-title").textContent = p.title;
  document.getElementById("play-meta").textContent = `난이도: ${p.difficulty}`;
  document.getElementById("play-scenario").textContent = p.scenario;

  const saved = loadConvo(id);
  if (saved && Array.isArray(saved.entries) && saved.entries.length) {
    // 저장된 대화 복원 — 카운트는 엔트리에서 직접 세어 어긋남 방지(힌트 계속 되는 버그 차단)
    convo = { entries: saved.entries };
    hintsUsed = saved.entries.filter(e => e.kind === "hint").length;
    questionCount = saved.entries.filter(e => e.kind === "my").length;
    solved = !!saved.solved || saved.entries.some(e => e.kind === "solution");
    saved.entries.forEach(renderEntry);
  } else {
    // 새 대화
    convo = { entries: [] };
    questionCount = 0; hintsUsed = 0; solved = false;
    appendBotText("이 사건의 진상을 추리해 보세요. 예/아니오 질문을 던지다가, 진상을 알겠으면 한 문장으로 설명해 보세요 — 맞으면 정답으로 인정돼요. (그냥 '정답'이라고만 쓰면 안 돼요!)");
  }

  document.getElementById("q-count").textContent = String(questionCount);
  document.getElementById("hint-n").textContent = String(hintsUsed);
  const input = document.getElementById("question-input");
  input.value = "";
  input.disabled = solved;
  document.getElementById("ask-btn").disabled = solved;
  document.getElementById("hint-btn").disabled = solved || hintsUsed >= 3;

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
  const content = document.querySelector(".content");
  const nearBottom = !content || (content.scrollHeight - content.scrollTop - content.clientHeight < 80);
  document.getElementById("chat-log").appendChild(row);
  if (content && nearBottom) content.scrollTop = content.scrollHeight;
}

function textNode(t) {
  const d = document.createElement("div");
  d.className = "msg-text";
  d.textContent = t;
  return d;
}

function renderMy(text) {
  const me = meName();
  msgRow("me", me, me.charAt(0).toUpperCase(), textNode(text));
}
function appendMyMsg(text) { pushEntry({ kind: "my", text }); }

function renderBotText(text) {
  msgRow("bot", "추리비서 AI", "AI", textNode(text));
}
function appendBotText(text) { pushEntry({ kind: "botText", text }); }

function renderBotVerdict(verdict) {
  const pill = document.createElement("span");
  pill.className = "verdict " + verdict.toLowerCase();
  pill.textContent = VERDICT_LABEL[verdict] || verdict;
  msgRow("bot", "추리비서 AI", "AI", pill);
}
function appendBotVerdict(verdict) { pushEntry({ kind: "verdict", verdict }); }

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

  aborter = new AbortController();
  try {
    const res = await fetch(`/api/ai/${currentPuzzleId}/ask`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: q }),
      signal: aborter.signal
    });
    if (res.status === 429) {
      appendBotText("🤖 AI가 지금 열일 중~ 질문이 몰렸어요! 1분에 20번까지만 받아요. 잠깐 쉬었다 다시 물어봐 줘 ><");
      return;
    }
    if (!res.ok) {
      appendBotText("AI 응답을 가져오지 못했어요. 잠시 후 다시 시도해 주세요.");
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
    if (e.name === "AbortError") return;   // 문제 나감/전환으로 취소된 요청은 무시
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

function renderBotSolution(text) {
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
function appendBotSolution(text) { pushEntry({ kind: "solution", text }); }

function renderBotHint(text, n) {
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
function appendBotHint(text, n) { pushEntry({ kind: "hint", text, n }); }

async function useHint() {
  if (solved || hintsUsed >= 3) return;
  const hintBtn = document.getElementById("hint-btn");
  const next = hintsUsed + 1;
  hintBtn.disabled = true;
  aborter = new AbortController();
  try {
    const res = await fetch(`/api/ai/${currentPuzzleId}/hint/${next}`, { method: "POST", signal: aborter.signal });
    if (res.status === 429) { appendBotText("🤖 AI가 열일 중~ 힌트가 몰렸어요! 잠깐 후 다시 눌러줘 (힌트는 안 차감됐어요) ><"); return; }
    if (!res.ok) { appendBotText("AI 힌트를 가져오지 못했어요. 잠시 후 다시 시도해 주세요."); return; }
    const data = await res.json();
    hintsUsed = next;
    appendBotHint(data.hint, next);
    document.getElementById("hint-n").textContent = String(hintsUsed);
  } catch (e) {
    if (e.name === "AbortError") return;   // 취소된 요청은 무시
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
  if (aborter) { aborter.abort(); aborter = null; }   // 목록으로 나가면 진행 중 요청 취소
  playView.classList.add("hidden");
  listView.classList.remove("hidden");
}

document.getElementById("ask-btn").addEventListener("click", ask);
document.getElementById("hint-btn").addEventListener("click", useHint);
document.getElementById("question-input").addEventListener("keydown", e => {
  if (e.key === "Enter" && !e.isComposing) ask();
});

loadList();
