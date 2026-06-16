// 공통 셸: 닉네임 없으면 입장 화면(/)으로 보내고, 있으면 사이드바/아바타에 표시.
// nickname.js 다음에 로드한다.
(function () {
  function init() {
    var nick = (typeof getNickname === "function" && getNickname()) || null;
    var path = location.pathname;
    var isHome = path === "/" || path === "/index.html" || path.endsWith("/index.html");

    if (!nick && !isHome) {
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
