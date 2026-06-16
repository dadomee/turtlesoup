// 닉네임은 localStorage에만 저장한다 (서버 계정 없음).
const NICK_KEY = "turtlesoup.nickname";

function getNickname() {
  return localStorage.getItem(NICK_KEY);
}

function setNickname(name) {
  localStorage.setItem(NICK_KEY, name);
}
