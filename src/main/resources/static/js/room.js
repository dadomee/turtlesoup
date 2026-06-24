const lobby = document.getElementById("lobby-view");
const roomView = document.getElementById("room-view");

let ws = null;
let isHost = false;
let isAiRoom = false;
let myCode = null;
let roomLog = [];      // 방 채팅 로그 캐시 (sessionStorage 복원용)
let roomEnded = false;
let aiHintTimer = null; // 힌트 버튼 잠금 안전 타이머
let hintShown = 0;      // 표시된 힌트 수 (비동기 역순 도착 대비 최댓값 유지)

const VERDICT = { YES: "예", NO: "아니오", IRRELEVANT: "상관없음", CORRECT: "정답!", UNKNOWN: "🤔 모호한 질문 — 더 구체적으로!" };
const VCLASS = { YES: "yes", NO: "no", IRRELEVANT: "irrelevant", CORRECT: "correct", UNKNOWN: "unknown" };

function me() {
  return (typeof getNickname === "function" && getNickname()) || "익명";
}

function judgeName() { return isAiRoom ? "추리비서 AI" : "출제자"; }

// 비차단 토스트 알림 (alert 대체)
function toast(msg) {
  let t = document.getElementById("toast");
  if (!t) { t = document.createElement("div"); t.id = "toast"; t.className = "toast"; document.body.appendChild(t); }
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => t.classList.remove("show"), 2800);
}

// ===== 방 대화 저장/복원 (sessionStorage — 탭 새로고침 견딤, 탭 닫으면 정리) =====
const ROOM_KEY = "turtlesoup.room";
const ROOM_TTL_MS = 60 * 60 * 1000; // 나간 뒤에도 이 시간 안에는 복원 가능 (기본 1시간)
function persistRoom() {
  if (!myCode) return;
  try {
    sessionStorage.setItem(ROOM_KEY, JSON.stringify({
      code: myCode, isHost, isAiRoom, ended: roomEnded, log: roomLog, savedAt: Date.now()
    }));
  } catch (e) {}
}
function clearPersistedRoom() {
  try { sessionStorage.removeItem(ROOM_KEY); } catch (e) {}
}
function renderLogEntry(e) {
  if (e.k === "chat") appendMsg("me", e.name, e.text);
  else if (e.k === "question") appendMsg("me", e.name, "❓ " + e.text);
  else if (e.k === "verdict") appendVerdict(e.verdict);
  else if (e.k === "hint") appendHint(e.text, e.count);
  else if (e.k === "solution") appendSolution(e.text);
}
function addChat(entry) {  // 렌더 + 캐시 + 저장 (시스템 알림은 캐시 안 함)
  renderLogEntry(entry);
  roomLog.push(entry);
  persistRoom();
}
function connectWs(code) {
  const proto = location.protocol === "https:" ? "wss" : "ws";
  ws = new WebSocket(`${proto}://${location.host}/ws/room/${code}`);
  ws.onopen = () => ws.send(JSON.stringify({ type: "join", nickname: me() }));
  ws.onmessage = (e) => handleEvent(JSON.parse(e.data));
  ws.onclose = () => appendSystem("연결이 종료되었습니다.");
}
function restoreEnterRoom(code, hasPuzzle) {
  myCode = code;
  resetRoomControls();
  document.getElementById("room-code-badge").textContent = "방 코드: " + code;
  document.getElementById("room-log").textContent = "";
  ["host-solution", "participant-composer", "host-controls", "ai-hint-bar", "puzzle-picker", "end-actions"]
    .forEach(id => document.getElementById(id).classList.add("hidden"));
  document.getElementById("room-title").textContent = "대기 중…";
  if (!hasPuzzle) {
    if (isAiRoom) {
      document.getElementById("room-scenario").textContent = "🤖 AI가 문제를 준비하고 있어요…";
    } else if (isHost) {
      document.getElementById("room-scenario").textContent = "문제를 고르면 게임이 시작됩니다.";
      document.getElementById("puzzle-picker").classList.remove("hidden");
    } else {
      document.getElementById("room-scenario").textContent = "출제자가 문제를 고르는 중입니다…";
    }
  } else {
    document.getElementById("room-scenario").textContent = "다시 연결 중…";
  }
  roomLog.forEach(renderLogEntry);   // 캐시된 지난 대화 복원
  lobby.classList.add("hidden");
  roomView.classList.remove("hidden");
  document.body.classList.add("in-room");
  connectWs(code);                   // 재연결 → 서버가 puzzle/solution 다시 보냄
  if (roomEnded) { endGame(); showEndActions(); }
}
function tryRestoreRoom() {
  let saved;
  try { saved = JSON.parse(sessionStorage.getItem(ROOM_KEY)); } catch (e) { saved = null; }
  if (!saved || !saved.code) return;
  if (saved.savedAt && Date.now() - saved.savedAt > ROOM_TTL_MS) { clearPersistedRoom(); return; }
  fetch(`/api/rooms/${saved.code}`)            // 방이 아직 살아있는지 확인
    .then(res => res.ok ? res.json() : Promise.reject())
    .then(info => {
      isHost = (info.hostName === me());
      isAiRoom = !!info.aiHosted;
      roomEnded = !!saved.ended;
      roomLog = Array.isArray(saved.log) ? saved.log : [];
      restoreEnterRoom(info.code, !!info.scenario);
    })
    .catch(() => clearPersistedRoom());        // 방 종료/서버 꺼짐 → 로비 유지
}

async function loadPuzzleOptions() {
  const res = await fetch("/api/puzzles");
  if (!res.ok) return;
  const puzzles = await res.json();
  const sel = document.getElementById("puzzle-select");
  sel.textContent = "";
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

async function createRoom(aiHosted) {
  const res = await fetch("/api/rooms", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ hostName: me(), aiHosted: !!aiHosted })
  });
  if (!res.ok) { toast("방 생성에 실패했습니다."); return; }
  const data = await res.json();
  isHost = true;
  isAiRoom = !!aiHosted;
  enterRoom(data.code);
}

async function joinRoom() {
  const code = document.getElementById("join-code").value.trim().toUpperCase();
  if (!code) return;
  const res = await fetch(`/api/rooms/${code}`);
  if (!res.ok) { toast("그런 방이 없습니다. 코드를 확인하세요."); return; }
  const info = await res.json();
  if ((info.participants || []).includes(me())) {
    toast("'" + me() + "' 닉네임은 이미 이 방에 있어요. 홈에서 이름을 바꿔 입장하세요.");
    return;
  }
  isHost = (info.hostName === me());
  isAiRoom = !!info.aiHosted;
  enterRoom(info.code);
}

function enterRoom(code) {
  myCode = code;
  roomLog = [];
  roomEnded = false;
  resetRoomControls();
  document.getElementById("room-code-badge").textContent = "방 코드: " + code;
  document.getElementById("room-log").textContent = "";
  ["host-solution", "participant-composer", "host-controls", "ai-hint-bar", "puzzle-picker", "end-actions"]
    .forEach(id => document.getElementById(id).classList.add("hidden"));
  document.getElementById("room-title").textContent = "대기 중…";

  if (isAiRoom) {
    document.getElementById("room-scenario").textContent = "🤖 AI가 문제를 준비하고 있어요…";
  } else if (isHost) {
    document.getElementById("room-scenario").textContent = "문제를 고르면 게임이 시작됩니다.";
    document.getElementById("puzzle-picker").classList.remove("hidden");
  } else {
    document.getElementById("room-scenario").textContent = "출제자가 문제를 고르는 중입니다…";
  }

  lobby.classList.add("hidden");
  roomView.classList.remove("hidden");
  document.body.classList.add("in-room");

  connectWs(code);
  persistRoom();
}

function setPuzzle() {
  if (!ws) return;
  const src = document.querySelector('input[name="src"]:checked').value;
  if (src === "existing") {
    const pid = Number(document.getElementById("puzzle-select").value);
    ws.send(JSON.stringify({ type: "setPuzzle", nickname: me(), puzzleId: pid }));
  } else {
    const scenario = document.getElementById("custom-scenario").value.trim();
    const solution = document.getElementById("custom-solution").value.trim();
    if (!scenario || !solution) { toast("상황과 정답을 입력하세요."); return; }
    const title = document.getElementById("custom-title").value.trim();
    ws.send(JSON.stringify({ type: "setPuzzle", nickname: me(), title, scenario, solution }));
  }
}

function handleEvent(ev) {
  if (ev.type === "system") {
    appendSystem(ev.text);                                  // 알림 — 캐시 안 함
    if (Array.isArray(ev.participants)) updateParticipants(ev.participants);
  }
  else if (ev.type === "puzzle") onPuzzle(ev.title, ev.scenario, ev.hintsUsed);
  else if (ev.type === "solution") showHostSolution(ev.solution);
  else if (ev.type === "chat") addChat({ k: "chat", name: ev.nickname, text: ev.text });
  else if (ev.type === "question") addChat({ k: "question", name: ev.nickname, text: ev.text });
  else if (ev.type === "answer") addChat({ k: "verdict", verdict: ev.verdict });
  else if (ev.type === "hint") addChat({ k: "hint", text: ev.text, count: ev.count });
  else if (ev.type === "newgame") restartGame();
  else if (ev.type === "reveal") {
    addChat({ k: "solution", text: ev.solution });
    roomEnded = true;
    persistRoom();
    endGame();
    showEndActions();
  }
}

function onPuzzle(title, scenario, hintsUsed) {
  document.getElementById("room-title").textContent = title;
  document.getElementById("room-scenario").textContent = scenario;
  document.getElementById("puzzle-picker").classList.add("hidden");
  document.getElementById("participant-composer").classList.remove("hidden");
  if (isAiRoom) {
    document.getElementById("ai-hint-bar").classList.remove("hidden");
  } else if (isHost) {
    document.getElementById("host-controls").classList.remove("hidden");
  }
  const who = isAiRoom ? "AI" : "출제자";
  appendSystem(`💬 자유롭게 대화하세요. ${who}에게 예/아니오 질문은 '/' 만 치고 이어서 입력하면 돼요.`);
  if (typeof hintsUsed === "number" && hintsUsed > 0) setHintCount(hintsUsed); // 늦게 입장해도 힌트 소진상태 반영(다 쓰면 잠금)
}

function showHostSolution(solution) {
  document.getElementById("host-solution-text").textContent = solution;
  document.getElementById("host-solution").classList.remove("hidden");
}

function updateParticipants(list) {
  const el = document.getElementById("room-participants");
  if (!el) return;
  el.textContent = list.length ? `👥 ${list.length}명: ${list.join(", ")}` : "";
}

// #room-log가 스크롤 컨테이너 (페이지 전체가 아니라 채팅창만 스크롤)
function row(side, name, contentEl) {
  const log = document.getElementById("room-log");
  const nearBottom = !log || (log.scrollHeight - log.scrollTop - log.clientHeight < 80);
  const r = document.createElement("div");
  r.className = "msg";
  const av = document.createElement("div");
  av.className = "msg-avatar " + side;
  av.textContent = name.charAt(0).toUpperCase();
  const body = document.createElement("div");
  const nm = document.createElement("div");
  nm.className = "msg-name";
  nm.textContent = name;
  body.append(nm, contentEl);
  r.append(av, body);
  log.appendChild(r);
  if (log && nearBottom) log.scrollTop = log.scrollHeight;
}

function textDiv(t) {
  const d = document.createElement("div");
  d.className = "msg-text";
  d.textContent = t;
  return d;
}

function appendMsg(side, name, text) { row(side, name, textDiv(text)); }

function appendSystem(text) {
  const log = document.getElementById("room-log");
  const nearBottom = !log || (log.scrollHeight - log.scrollTop - log.clientHeight < 80);
  const p = document.createElement("p");
  p.className = "muted";
  p.style.textAlign = "center";
  p.textContent = text;
  log.appendChild(p);
  if (log && nearBottom) log.scrollTop = log.scrollHeight;
}

function appendVerdict(v) {
  const pill = document.createElement("span");
  pill.className = "verdict " + (VCLASS[v] || "unknown");
  pill.textContent = VERDICT[v] || v;
  row("bot", judgeName(), pill);
}

function appendSolution(text) {
  const box = document.createElement("div");
  box.className = "msg-text";
  box.style.background = "var(--info-bg)";
  box.style.borderRadius = "8px";
  box.style.padding = "10px 12px";
  box.style.lineHeight = "1.7";
  box.textContent = text;
  row("bot", judgeName(), box);
}

function appendHint(text, count) {
  clearTimeout(aiHintTimer);   // 힌트 도착 → 잠금 안전 타이머 해제
  const box = document.createElement("div");
  box.className = "msg-text";
  const label = document.createElement("div");
  label.style.fontWeight = "500";
  label.style.marginBottom = "4px";
  label.textContent = `💡 힌트 ${count}/3`;
  const body = document.createElement("div");
  body.style.background = "var(--warn-bg)";
  body.style.color = "var(--warn)";
  body.style.borderRadius = "8px";
  body.style.padding = "10px 12px";
  body.style.lineHeight = "1.7";
  body.textContent = text;
  box.append(label, body);
  row("bot", judgeName() + " 힌트", box);
  setHintCount(count);
}

// 힌트 카운트: 최댓값 유지(역순 도착 대비), 3이면 잠금 고정
function setHintCount(count) {
  hintShown = Math.max(hintShown, count || 0);
  ["hint-count", "ai-hint-count"].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.textContent = hintShown;
  });
  if (hintShown >= 3) {
    ["hint-send", "hint-input", "ai-hint-btn"].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.disabled = true;
    });
  } else {
    const ab = document.getElementById("ai-hint-btn");
    if (ab && !roomEnded) ab.disabled = false;
  }
}

// 방 진입/복원 시 힌트 카운트·버튼 상태를 깨끗이 초기화 (이전 방 값 누적 방지)
function resetRoomControls() {
  hintShown = 0;
  ["hint-count", "ai-hint-count"].forEach(id => { const el = document.getElementById(id); if (el) el.textContent = "0"; });
  ["room-input", "room-send", "hint-input", "hint-send", "ai-hint-btn", "ai-reveal-btn"].forEach(id => { const el = document.getElementById(id); if (el) el.disabled = false; });
  document.querySelectorAll("#host-controls button").forEach(b => b.disabled = false);
}

function endGame() {
  ["room-input", "room-send", "hint-input", "hint-send", "ai-hint-btn", "ai-reveal-btn"].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.disabled = true;
  });
  document.querySelectorAll("#host-controls button").forEach(b => b.disabled = true);
}

// 게임 종료 후 옵션(새 게임 / 나가기) 노출
function showEndActions() {
  const ng = document.getElementById("newgame-btn");
  if (ng) ng.classList.toggle("hidden", !(isAiRoom || isHost)); // 사람 방 참가자는 새 게임 불가
  const ea = document.getElementById("end-actions");
  if (ea) ea.classList.remove("hidden");
}

// 새 게임: 화면 초기화 (이어서 서버가 새 문제를 보내거나, 호스트가 다시 고름)
function restartGame() {
  roomLog = []; roomEnded = false; hintShown = 0;
  clearTimeout(aiHintTimer);
  document.getElementById("room-log").textContent = "";
  ["host-solution", "host-controls", "end-actions", "participant-composer", "ai-hint-bar", "puzzle-picker"]
    .forEach(id => document.getElementById(id).classList.add("hidden"));
  ["hint-count", "ai-hint-count"].forEach(id => { const el = document.getElementById(id); if (el) el.textContent = "0"; });
  ["room-input", "room-send", "hint-input", "hint-send", "ai-hint-btn", "ai-reveal-btn"].forEach(id => { const el = document.getElementById(id); if (el) el.disabled = false; });
  document.querySelectorAll("#host-controls button").forEach(b => b.disabled = false);
  document.getElementById("room-title").textContent = "대기 중…";
  if (isAiRoom) {
    document.getElementById("room-scenario").textContent = "🤖 AI가 새 문제를 준비하고 있어요…";
  } else if (isHost) {
    document.getElementById("room-scenario").textContent = "새 문제를 고르면 다시 시작됩니다.";
    document.getElementById("puzzle-picker").classList.remove("hidden");
  } else {
    document.getElementById("room-scenario").textContent = "출제자가 새 문제를 고르는 중입니다…";
  }
  persistRoom();
}

const ASK_PREFIX = "/질문";
function sendQuestion() {
  const input = document.getElementById("room-input");
  const raw = input.value.trim();
  if (!raw || !ws) return;
  if (raw.startsWith(ASK_PREFIX)) {
    // "/질문 ..." → AI/출제자에게 묻기 (판정 대상)
    const q = raw.slice(ASK_PREFIX.length).trim();
    if (!q) return; // "/질문"만 입력하면 무시
    ws.send(JSON.stringify({ type: "ask", nickname: me(), text: q }));
  } else {
    // 그냥 입력 → 잡담 (판정 안 함)
    ws.send(JSON.stringify({ type: "chat", nickname: me(), text: raw }));
  }
  input.value = "";
}

function sendAnswer(verdict) {
  if (ws) ws.send(JSON.stringify({ type: "answer", nickname: me(), verdict }));
}

function sendHint() {
  const inp = document.getElementById("hint-input");
  const t = inp.value.trim();
  if (!t || !ws) return;
  ws.send(JSON.stringify({ type: "hint", nickname: me(), text: t }));
  inp.value = "";
}

function leaveRoom() {
  if (ws) ws.close();
  persistRoom();                  // 나가도 일정 시간(ROOM_TTL_MS) 안에는 다시 들어가면 복원
  myCode = null; roomLog = []; roomEnded = false;
  document.body.classList.remove("in-room");
  roomView.classList.add("hidden");
  lobby.classList.remove("hidden");
}

document.getElementById("create-human-btn").addEventListener("click", () => createRoom(false));
document.getElementById("create-ai-btn").addEventListener("click", () => createRoom(true));
document.getElementById("join-btn").addEventListener("click", joinRoom);
document.getElementById("join-code").addEventListener("keydown", e => { if (e.key === "Enter" && !e.isComposing) joinRoom(); });
document.getElementById("set-puzzle-btn").addEventListener("click", setPuzzle);
document.getElementById("random-puzzle-btn").addEventListener("click", () => {
  if (!ws) return;
  const sel = document.getElementById("puzzle-select");
  if (!sel.options.length) return;
  const pid = Number(sel.options[Math.floor(Math.random() * sel.options.length)].value);
  ws.send(JSON.stringify({ type: "setPuzzle", nickname: me(), puzzleId: pid }));
});
document.getElementById("room-send").addEventListener("click", sendQuestion);
document.getElementById("room-input").addEventListener("keydown", e => { if (e.key === "Enter" && !e.isComposing) sendQuestion(); });
// "/" 새로 입력했을 때만 "/질문 "으로 자동완성 (지우다가 "/"가 돼도 다시 안 붙게)
document.getElementById("room-input").addEventListener("input", e => {
  if (e.target.value === "/" && (e.inputType || "").startsWith("insert")) e.target.value = ASK_PREFIX + " ";
});
document.querySelectorAll("#host-controls .ans").forEach(b => {
  b.addEventListener("click", () => sendAnswer(b.dataset.v));
});
document.getElementById("reveal-btn").addEventListener("click", () => {
  if (ws) ws.send(JSON.stringify({ type: "reveal", nickname: me() }));
});
document.getElementById("hint-send").addEventListener("click", sendHint);
document.getElementById("hint-input").addEventListener("keydown", e => { if (e.key === "Enter" && !e.isComposing) sendHint(); });
document.getElementById("ai-hint-btn").addEventListener("click", (e) => {
  const btn = e.currentTarget;
  if (!ws || btn.disabled) return;
  btn.disabled = true; // 힌트 도착할 때까지 잠금 (연타·낭비·역순 방지)
  clearTimeout(aiHintTimer);
  aiHintTimer = setTimeout(() => { if (!roomEnded && hintShown < 3) btn.disabled = false; }, 30000);
  ws.send(JSON.stringify({ type: "hint", nickname: me() }));
});
document.getElementById("ai-reveal-btn").addEventListener("click", () => {
  if (ws) ws.send(JSON.stringify({ type: "reveal", nickname: me() }));
});
document.getElementById("newgame-btn").addEventListener("click", () => {
  if (ws) ws.send(JSON.stringify({ type: "newGame", nickname: me() }));
});

loadPuzzleOptions();
tryRestoreRoom();   // 새로고침했으면 이전 방에 자동 재입장 + 대화 복원
