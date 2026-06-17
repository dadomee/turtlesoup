// 위장용 회사(워크스페이스) 이름. localStorage에 저장해 모든 페이지에 적용.
(function () {
  var KEY = "turtlesoup.company";
  function getCompany() { return localStorage.getItem(KEY) || "(주)성실상사"; }
  function applyCompany() {
    document.querySelectorAll(".ws-header").forEach(function (el) {
      el.textContent = getCompany();
    });
  }
  function setCompany(v) {
    var name = (v || "").trim();
    if (name) localStorage.setItem(KEY, name); else localStorage.removeItem(KEY);
    applyCompany();
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", applyCompany);
  } else {
    applyCompany();
  }
  window.getCompany = getCompany;
  window.setCompany = setCompany;
})();
