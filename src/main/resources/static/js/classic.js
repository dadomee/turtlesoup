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

  listView.classList.add("hidden");
  playView.classList.remove("hidden");
}

function showList() {
  playView.classList.add("hidden");
  listView.classList.remove("hidden");
}

loadList();
