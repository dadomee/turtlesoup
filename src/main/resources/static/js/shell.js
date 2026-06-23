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
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
