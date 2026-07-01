// 공통 셸: 닉네임 없으면 입장 화면(/)으로 보내고, 있으면 사이드바/아바타에 표시.
// nickname.js 다음에 로드한다.
(function () {
  function init() {
    var nick = (typeof getNickname === "function" && getNickname()) || null;
    var path = location.pathname;
    // 홈과 게임 방법 페이지는 닉네임 없이도 볼 수 있다
    var isHome = path === "/" || path === "/index.html" || path.endsWith("/index.html") || path.endsWith("/guide.html");

    if (!nick && !isHome) {
      alert("먼저 이름(닉네임)을 정해야 입장할 수 있어요. 홈으로 이동합니다.");
      location.href = "/";
      return;
    }
    if (nick) {
      document.querySelectorAll("[data-me-name]").forEach(function (el) {
        el.textContent = nick;
      });
      document.querySelectorAll("[data-me-avatar]").forEach(function (el) {
        el.textContent = nick.charAt(0).toUpperCase();
      });
    }
    setupMobileNav();
  }

  // 모바일: 상단바에 ☰ 버튼을 넣고 사이드바를 오프캔버스로 토글
  function setupMobileNav() {
    var topbar = document.querySelector(".topbar");
    var sidebar = document.querySelector(".sidebar");
    if (!topbar || !sidebar || topbar.querySelector(".menu-btn")) return;

    var btn = document.createElement("button");
    btn.className = "menu-btn";
    btn.setAttribute("aria-label", "메뉴");
    btn.textContent = "☰";
    topbar.insertBefore(btn, topbar.firstChild);

    var backdrop = document.createElement("div");
    backdrop.className = "nav-backdrop";
    document.body.appendChild(backdrop);

    function close() { sidebar.classList.remove("open"); backdrop.classList.remove("show"); }
    function open() { sidebar.classList.add("open"); backdrop.classList.add("show"); }
    btn.addEventListener("click", function () {
      sidebar.classList.contains("open") ? close() : open();
    });
    backdrop.addEventListener("click", close);
    sidebar.querySelectorAll(".nav-item").forEach(function (a) {
      a.addEventListener("click", close);
    });
  }
  // 모바일 키보드가 올라와도 상단(문제)·하단(입력)이 안 밀리게:
  // 앱 높이를 '실제 보이는 영역(visualViewport)'에 맞춰 키보드 뒤로 안 늘어나게 한다.
  function setupViewportFit() {
    var vv = window.visualViewport;
    if (!vv) return;
    var root = document.documentElement;
    function fit() {
      root.style.setProperty("--app-h", vv.height + "px");
      root.style.setProperty("--app-top", vv.offsetTop + "px");
    }
    vv.addEventListener("resize", fit);
    vv.addEventListener("scroll", fit);
    fit();
  }
  setupViewportFit();

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
