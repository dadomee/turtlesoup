<div align="center">

# 🐢 바다거북스프 · Turtle Soup

**업무시간에 몰래 하는 수평적 사고 추리 게임**

_예 / 아니오 질문만 던져 숨겨진 진상을 파헤치세요.
들킬 것 같으면 보스키(`` ` ``) 한 방으로 가짜 Outlook 뒤에 숨고요._ 🕵️

<br>

[![▶ 라이브 데모](https://img.shields.io/badge/▶_라이브_데모-turtlesoup-4f52c9?style=for-the-badge&logo=render&logoColor=white)](https://turtlesoup-vkpa.onrender.com)

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-1.0-6DB33F?logo=spring&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-실시간_멀티플레이-010101?logo=socketdotio&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Render_배포-2496ED?logo=docker&logoColor=white)

</div>

---

## 🕵️ 바다거북스프가 뭐예요?

"바다거북스프"는 **수평적 사고 퍼즐**(situation puzzle)이에요.
출제자는 기묘한 **상황 한 조각**만 던지고, 플레이어는 오직 **예 / 아니오 / 상관없음**으로 답할 수 있는 질문을 던지며 숨은 **진상(眞相)**을 복원해 나갑니다.

> 🍜 이름의 유래: *"어느 남자가 식당에서 바다거북 수프를 한입 먹고, 집에 돌아가 스스로 목숨을 끊었다. 왜?"* — 이런 식의 문제를 파고드는 놀이라서 '바다거북스프'예요.

여기에 **"업무시간에 몰래 한다"** 는 컨셉을 얹었어요. 화면 전체가 회사 메신저(팀즈/슬랙)처럼 위장되어 있고, 상사가 지나가면 **보스키** 한 번으로 순식간에 업무 메일함으로 변신합니다. 😏

<br>

## ✨ 세 가지 플레이 모드

| 모드 | 채널 | 설명 |
|:---:|:---|:---|
| 📁 **문제-보관함** | `# 문제-보관함` | 검증된 **37개** 문제를 혼자 풀고 정답을 확인. 막히면 **단계별 힌트 3회**. |
| 🤖 **추리비서 AI** | `@ 추리비서 AI` | 자유롭게 질문하면 **AI가 예/아니오로 판정**. 진상을 한 문장으로 설명하면 정답 인정! |
| 👥 **팀-라운지** | `# 팀-라운지` | 4자리 방 코드로 **실시간 멀티플레이**. **사람 출제** 또는 **AI 출제(다같이 깜깜이 추리)** 선택. |

- 🔑 **가입 없음** — 닉네임만 정하면 바로 플레이 (localStorage에 저장)
- 💬 대화는 자동 저장·복원 (솔로=영구 / 방=1시간)
- 📱 **모바일 반응형** — 폰에서도 그대로, 앱 전환해도 소켓 자동 재연결

<br>

## 🎭 "업무시간 몰래" 위장 장치

이 프로젝트의 시그니처 재미 요소예요.

- **🖥️ 메신저 위장 테마** — Teams(블루) ↔ Slack(퍼플) 전환. 사이드바 회사 이름도 직접 편집 가능.
- **📑 탭 제목 위장** — 브라우저 탭이 "Microsoft Teams" / "Slack"으로 표시. 보스키를 켜면 "받은 편지함 - Outlook"으로 바뀝니다.
- **🚨 보스키(`` ` ``)** — 누르는 순간 화면 전체가 **가짜 Outlook 받은편지함**으로 전환. (모바일은 하단 버튼 탭) 다시 누르면 게임으로 복귀.

<br>

## 🧠 AI는 어떻게 '판정'하나요?

단순 챗봇이 아니라, **누출 없이 공정하게 판정**하도록 설계했어요.

- LLM이 `{ "근거": ..., "판정": ... }` **구조화된 JSON**으로 답 → 서버가 **판정값만 추출**해 매핑
- 판정은 `예 / 아니오 / 상관없음 / 모호 / 정답` — 애매한 질문은 **"🤔 더 구체적으로"** 로 유도
- **"정답"이라고 외치기만 하면 안 됨** — 핵심 인과를 1~2개 짚어야 정답 인정 (가짜 정답 방지)
- 힌트도 **정답을 흘리지 않는 선에서** 1단계(막연) → 3단계(구체)로 생성

<br>

## 🏗️ 기술 스택 & 설계 포인트

| 영역 | 사용 기술 |
|:---|:---|
| **Backend** | Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation |
| **AI** | Spring AI 1.0 (`ChatClient`) — 로컬 Ollama · 배포 Google Gemini |
| **실시간** | 순수 WebSocket (`TextWebSocketHandler`) — STOMP·외부 JS 라이브러리 **0** |
| **DB** | H2 (로컬 인메모리) · PostgreSQL(Neon, 배포) |
| **Frontend** | Vanilla JS + HTML + CSS (프레임워크 없음), localStorage/sessionStorage |
| **Infra** | Docker · Render(앱) · Neon(DB) |

### 눈여겨볼 설계 결정

- **🔀 프로필 하나로 로컬 ↔ 배포 전환 (코드 0줄 수정)**
  `local` = Ollama + H2, `prod` = Gemini + Postgres. 의존성에 두 AI 스타터를 모두 두고 `spring.ai.model.chat` 값으로 골라 씁니다.
- **🧩 Spring AI로 Gemini 붙이기 (전용 스타터 없이)**
  Spring AI 1.0엔 Google 스타터가 없어서, **OpenAI 스타터를 Gemini의 OpenAI-호환 엔드포인트**에 물렸어요. `ChatClient` 코드는 그대로.
- **🔒 정답 누출 방지 설계**
  정답은 별도 엔드포인트(`/solution`)·출제자에게만. 플레이용 DTO엔 정답·힌트가 애초에 없습니다.
- **🌐 의존성 없는 실시간 멀티플레이**
  메모리 기반 `RoomService` + 4자리 방 코드 + 순수 WebSocket. 백그라운드 전환 시 자동 재연결.
- **🙅 무가입 캐주얼 UX**
  닉네임만으로 플레이, 대화는 브라우저 저장소로 복원. 로그인 흐름의 마찰을 제거.

<br>

## 🚀 로컬에서 실행하기

> 기본 프로필은 `local` — H2 인메모리 DB에 문제 37개가 **자동 시드**됩니다.

```bash
# 1) 로컬 AI 준비 (Ollama)  — '추리비서 AI'·'AI 출제 방'에 필요
ollama serve
ollama pull qwen2.5:7b

# 2) 앱 실행
./gradlew bootRun        # → http://localhost:8080
```

- 📁 문제-보관함은 AI 없이도 바로 플레이 가능
- 📱 같은 와이파이의 폰에서 접속: `http://<로컬IP>:8080`

<br>

## ☁️ 배포 구조

```
브라우저 ──▶ Render (Docker, Spring Boot) ──▶ Neon (PostgreSQL)
                     │
                     └──▶ Google Gemini  (OpenAI-호환 엔드포인트)
```

- **Render** — Docker 빌드로 앱 서빙 (프론트/백엔드 한 몸). 무료 요금제라 15분 무접속 시 슬립 → 첫 접속 시 콜드스타트가 잠깐 걸릴 수 있어요.
- **Neon** — 무료 PostgreSQL. 앱이 최초 기동 시 테이블 생성 + 문제 자동 시드.
- **Gemini** — 무료 티어. 코드 수정 없이 API 키·모델만 환경변수로 주입.

<br>

## 🗂️ 프로젝트 구조

```
src/main/java/com/turtlesoup
├── puzzle/     # 문제 엔티티·REST·시드(PuzzleSeeder)
├── ai/         # AI 판정·힌트 (AiJudgeService, ChatClient)
├── room/       # 실시간 멀티플레이 (RoomSocketHandler, RoomService)
├── history/    # 풀이 기록 (play_history)
└── config/     # WebSocket 등 설정

src/main/resources
├── static/     # 프론트엔드 (HTML·JS·CSS, 프레임워크 없음)
└── application*.yml   # 프로필별 설정 (local / prod)
```

<br>

## 🖼️ 스크린샷

<!-- docs/screenshots/ 에 이미지를 넣고 아래 주석을 풀어 주세요
<div align="center">
  <img src="docs/screenshots/ai.png"    width="30%" alt="추리비서 AI" />
  <img src="docs/screenshots/room.png"  width="30%" alt="팀-라운지" />
  <img src="docs/screenshots/boss.png"  width="30%" alt="보스키 (가짜 Outlook)" />
</div>
-->

_👉 지금 바로 체험: **https://turtlesoup-vkpa.onrender.com**_

<br>

<div align="center">
<sub>업무시간에 들키지 않게, 즐거운 추리 되세요 🐢🤫</sub>
</div>
