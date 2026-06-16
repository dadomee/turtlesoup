const listView = document.getElementById("list-view");
const playView = document.getElementById("play-view");

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

    // 사용자 입력 텍스트가 들어올 미래 모드에 대비해 textContent로 안전하게 렌더링
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
  if (!res.ok) {
    alert("문제를 불러오지 못했습니다.");
    return;
  }
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
    if (!r.ok) {
      alert("정답을 불러오지 못했습니다.");
      return;
    }
    const s = await r.json();
    document.getElementById("play-solution").textContent = s.solution;
    solutionCard.classList.remove("hidden");
    revealBtn.classList.add("hidden");
  };

  // 힌트 (문제마다 최대 3회)
  let hintsUsed = 0;
  const hintBtn = document.getElementById("hint-btn");
  const hintN = document.getElementById("hint-n");
  const hintList = document.getElementById("hint-list");
  hintN.textContent = "0";
  hintList.textContent = "";
  hintBtn.disabled = false;
  hintBtn.onclick = async () => {
    if (hintsUsed >= 3) return;
    const next = hintsUsed + 1;
    const hr = await fetch(`/api/puzzles/${id}/hint/${next}`);
    if (!hr.ok) { alert("힌트를 불러오지 못했습니다."); return; }
    const hd = await hr.json();
    if (!hd.hint) { alert("이 문제에는 더 이상 힌트가 없습니다."); hintBtn.disabled = true; return; }
    hintsUsed = next;
    const card = document.createElement("div");
    card.className = "card";
    card.style.background = "var(--warn-bg)";
    card.style.color = "var(--warn)";
    const b = document.createElement("b");
    b.textContent = `💡 힌트 ${next}/3`;
    const pp = document.createElement("p");
    pp.style.margin = "4px 0 0";
    pp.style.lineHeight = "1.7";
    pp.textContent = hd.hint;
    card.append(b, pp);
    hintList.appendChild(card);
    hintN.textContent = String(hintsUsed);
    if (hintsUsed >= 3) hintBtn.disabled = true;
  };

  listView.classList.add("hidden");
  playView.classList.remove("hidden");
}

function showList() {
  playView.classList.add("hidden");
  listView.classList.remove("hidden");
}

loadList();
