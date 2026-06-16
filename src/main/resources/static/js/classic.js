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
