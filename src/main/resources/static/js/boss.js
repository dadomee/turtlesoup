// 보스키: 백틱(`) 키 또는 우측 하단 힌트 클릭 → 가짜 Outlook 받은편지함으로 즉시 전환.
// 다시 누르면 복귀. 탭 제목도 함께 위장한다.
(function () {
  const REAL_TITLE = document.title;
  const BOSS_TITLE = "받은 편지함 - Outlook";

  const overlay = document.createElement("div");
  overlay.className = "boss-overlay";
  overlay.innerHTML = `
    <div style="display:flex;flex-direction:column;height:100%;font-family:'Segoe UI',system-ui,'Malgun Gothic',sans-serif;color:#252423;">
      <div style="background:#0f6cbd;color:#fff;padding:8px 16px;display:flex;align-items:center;gap:12px;font-size:13px;">
        <b style="font-weight:600;">Outlook</b>
        <div style="flex:1;max-width:520px;background:rgba(255,255,255,.2);border-radius:4px;padding:5px 10px;font-size:12px;">메일 및 사람 검색</div>
        <span>김다솜</span>
      </div>
      <div class="ob-body" style="display:flex;flex:1;min-height:0;">
        <div class="ob-folders" style="background:#f3f2f1;border-right:1px solid #e1dfdd;padding:10px 8px;font-size:13px;">
          <div style="font-weight:600;margin:6px 8px;">즐겨찾기</div>
          <div style="padding:7px 10px;border-radius:4px;background:#e1eaf7;font-weight:600;">받은 편지함 <span style="float:right;color:#0f6cbd;">12</span></div>
          <div style="padding:7px 10px;color:#444;">보낸 편지함</div>
          <div style="padding:7px 10px;color:#444;">임시 보관함 <span style="float:right;color:#888;">3</span></div>
          <div style="padding:7px 10px;color:#444;">삭제된 항목</div>
          <div style="padding:7px 10px;color:#444;">정크 메일</div>
          <div style="font-weight:600;margin:14px 8px 6px;">폴더</div>
          <div style="padding:7px 10px;color:#444;">프로젝트_A</div>
          <div style="padding:7px 10px;color:#444;">결재문서</div>
        </div>
        <div class="ob-list" style="border-right:1px solid #e1dfdd;overflow:auto;font-size:13px;">
          ${mailListItem("재무팀", "[필독] 3분기 예산 집행 내역 확인 요청", "안녕하세요. 각 부서 3분기 예산 집행 내역을 첨부 양식에 맞추어...", "오전 9:14", true, true)}
          ${mailListItem("박상무", "주간업무보고 양식 변경 안내", "다음 주부터 주간보고는 신규 양식으로 제출 바랍니다. 기존...", "오전 8:52", true, false)}
          ${mailListItem("인사팀", "연차 사용 촉진 제도 안내", "미사용 연차 소진 계획서를 이번 주 금요일까지 제출해 주시기...", "어제", false, false)}
          ${mailListItem("IT지원팀", "[보안] 전사 보안 패치 적용 안내", "금일 18시 이후 전사 PC 보안 패치가 자동 적용될 예정입니다...", "어제", false, false)}
          ${mailListItem("김대리", "어제 회의록 공유드립니다", "회의록 정리해서 공유드립니다. 액션 아이템 확인 부탁드려요...", "수 14:30", false, false)}
          ${mailListItem("총무팀", "비품 신청 마감 안내", "이번 달 비품 신청은 25일까지입니다. 신청서는 그룹웨어에서...", "화 11:05", false, false)}
        </div>
        <div class="ob-pane" style="flex:1;min-width:0;padding:18px 24px;overflow:auto;">
          <div style="font-size:18px;font-weight:600;margin-bottom:8px;">[필독] 3분기 예산 집행 내역 확인 요청</div>
          <div style="display:flex;align-items:center;gap:10px;padding-bottom:12px;border-bottom:1px solid #e1dfdd;margin-bottom:14px;">
            <div style="width:36px;height:36px;border-radius:50%;background:#0f6cbd;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:600;">재</div>
            <div style="font-size:13px;"><b>재무팀</b> &lt;finance@seongsil.co.kr&gt;<br><span style="color:#666;">받는 사람: 전 부서</span></div>
            <div style="margin-left:auto;color:#888;font-size:12px;">오전 9:14</div>
          </div>
          <div style="font-size:13.5px;line-height:1.9;color:#333;">
            안녕하세요, 재무팀입니다.<br><br>
            각 부서별 3분기 예산 집행 내역을 첨부된 양식에 맞추어 작성하신 후
            <b>이번 주 금요일 오후 5시까지</b> 회신 부탁드립니다.<br><br>
            특히 항목별 집행률과 잔여 예산을 정확히 기재해 주시고, 초과 집행 건은
            사유서를 별도 첨부해 주시기 바랍니다.<br><br>
            협조에 감사드립니다.<br><br>
            재무팀 드림
          </div>
        </div>
      </div>
    </div>`;

  function mailListItem(sender, subject, preview, time, unread, selected) {
    return `<div style="padding:10px 14px;border-bottom:1px solid #edebe9;${selected ? "background:#e1eaf7;border-left:3px solid #0f6cbd;" : ""}">
      <div style="display:flex;justify-content:space-between;font-size:13px;">
        <span style="${unread ? "font-weight:600;" : "color:#444;"}">${sender}</span>
        <span style="color:#888;font-size:11px;">${time}</span>
      </div>
      <div style="${unread ? "font-weight:600;" : "color:#444;"};font-size:13px;margin-top:1px;">${subject}</div>
      <div style="color:#888;font-size:12px;margin-top:1px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${preview}</div>
    </div>`;
  }

  const hint = document.createElement("div");
  hint.className = "boss-hint";
  hint.textContent = "보스 출현 시 ` 키";

  function toggle() {
    const on = overlay.classList.toggle("show");
    document.title = on ? BOSS_TITLE : REAL_TITLE;
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.body.appendChild(overlay);
    document.body.appendChild(hint);
    hint.addEventListener("click", toggle);
    // 모바일엔 ` / Esc 키가 없으니, 가짜 Outlook 화면을 탭하면 게임으로 복귀
    overlay.addEventListener("click", toggle);
  });

  document.addEventListener("keydown", function (e) {
    if (e.key === "`") {
      e.preventDefault();
      toggle();
    } else if (e.key === "Escape" && overlay.classList.contains("show")) {
      toggle();
    }
  });
})();
