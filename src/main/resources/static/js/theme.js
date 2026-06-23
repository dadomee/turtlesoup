// 테마 전환(팀즈/슬랙). <head>에서 즉시 적용해 깜빡임을 막는다.
(function () {
  var KEY = "turtlesoup.theme";
  function getTheme() { return localStorage.getItem(KEY) || "teams"; }
  function titleFor(t) { return (t || getTheme()) === "slack" ? "Slack" : "Microsoft Teams"; }
  function applyTheme(t) {
    document.documentElement.setAttribute("data-theme", t || "teams");
    // 탭 제목도 테마에 맞춤 (단, 보스 오버레이가 떠 있으면 건드리지 않음)
    if (!document.querySelector(".boss-overlay.show")) document.title = titleFor(t);
  }
  function setTheme(t) { localStorage.setItem(KEY, t); applyTheme(t); }
  applyTheme(getTheme());
  window.getTheme = getTheme;
  window.setTheme = setTheme;
  window.appTitle = titleFor; // boss.js가 복귀 제목으로 사용
})();
