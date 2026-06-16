// 테마 전환(팀즈/슬랙). <head>에서 즉시 적용해 깜빡임을 막는다.
(function () {
  var KEY = "turtlesoup.theme";
  function getTheme() { return localStorage.getItem(KEY) || "teams"; }
  function applyTheme(t) { document.documentElement.setAttribute("data-theme", t || "teams"); }
  function setTheme(t) { localStorage.setItem(KEY, t); applyTheme(t); }
  applyTheme(getTheme());
  window.getTheme = getTheme;
  window.setTheme = setTheme;
})();
