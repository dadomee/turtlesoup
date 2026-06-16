# 바다거북스프 게임 웹 — 설계 문서

작성일: 2026-06-16

## 한 줄 요약

수평적 사고 추리 게임("바다거북스프")을 세 가지 모드로 즐길 수 있는 웹 애플리케이션.
공통 뼈대(문제 데이터 + 화면 틀) 위에 세 가지 "출제자" 모드를 모듈로 얹는다.

## 목표 / 비목표

**목표**
- 세 가지 모드 제공: ① 미리 만든 문제 + 정답 공개, ② AI 출제자, ③ 사람 출제자(멀티플레이)
- 무료로 개발하고 무료로 인터넷 배포
- 프론트엔드 부담을 최소화 (프레임워크 없는 순수 HTML/CSS/JS)
- Java/Spring 강점을 살린 포트폴리오 가치

**비목표 (YAGNI)**
- 회원가입/로그인 시스템 (1차 범위 아님 — 닉네임만)
- 모바일 앱
- AI가 문제를 *생성*하는 기능 (AI는 판정만)

## 입장 흐름 (No-auth, 일회성)

- **회원가입·로그인 없음.** 웹에 들어오면 닉네임만 정하고 바로 플레이.
- 첫 방문 시 닉네임 입력 → 브라우저 `localStorage`에 저장 → 모든 모드에서 재사용.
- 서버는 닉네임을 계정으로 보관하지 않는다. `play_history.nickname`에 기록 용도로만 남긴다.
- 닉네임은 언제든 바꿀 수 있다 (localStorage 갱신).
- 멀티플레이(③)에서는 이 닉네임이 방 안의 표시 이름이 된다.

## 기술 스택

| 영역 | 선택 | 이유 |
|------|------|------|
| 백엔드 | Spring Boot 4.1.0, Java 21, Gradle | 다솜님 강점, 기존 프로젝트(arkilo/askmynotes)와 일관 (Initializr 기본 버전이 4.1.0으로 잡힘 — 일부 테스트 import 경로가 Boot 4 기준) |
| 웹 | Spring Web (REST) | 모드 ①②의 API |
| AI | Spring AI `ChatClient` | 제공자 추상화 — 로컬 Ollama / 배포 Gemini, 추후 Claude 교체 가능 |
| 실시간 | Spring WebSocket (STOMP) | 모드 ③ 멀티플레이 |
| DB | Spring Data JPA — 로컬 H2 / 배포 Supabase Postgres | 코드 동일, 프로파일만 분리 |
| 프론트 | 순수 HTML + CSS + 바닐라 JS (+ STOMP.js) | 빌드 도구 없이 단순, 학습 친화적 |
| 배포 | Render(무료) 또는 Railway + Supabase + Gemini 무료 티어 | 무료, arkilo Docker 경험 재활용 |

### AI 제공자 전략
`ChatClient` 추상화 하나로 코드는 동일하게 유지하고, Spring 프로파일로 제공자만 교체한다.
- `local` 프로파일 → Ollama (무료, 로컬)
- `prod` 프로파일 → Google Gemini 무료 티어 (배포용, 항상 켜져 있음)
- (선택) 추후 Anthropic Claude API로 교체 시 의존성 + 설정만 변경

## 데이터 모델 (DB)

```
puzzle
  - id (PK)
  - title           문제 제목
  - scenario        "스프"(플레이어에게 보여줄 수수께끼 상황)
  - solution        정답/해설 (AI 판정 기준, 정답 공개 시 노출)
  - difficulty      난이도 (EASY/NORMAL/HARD)
  - tags            태그 (쉼표 구분 단일 문자열로 단순화)

play_history
  - id (PK)
  - puzzle_id (FK)
  - mode            CLASSIC / AI
  - nickname        플레이어 닉네임
  - question_count  정답까지 질문 수
  - solved          성공 여부
  - created_at
```

- 고전 바다거북스프 문제 약 10개를 시드 데이터로 미리 적재 (idempotent seeder).
- 멀티플레이 "방(Room)"은 휘발성이라 DB가 아닌 **메모리(in-memory)**에 저장.
  재시작하면 진행 중인 방은 사라지되, 문제·기록은 DB에 유지된다.

## 세 모드 동작

### ① 미리 만든 문제 + 정답 공개 (CLASSIC)
1. 문제 목록에서 하나 선택
2. 스프(scenario) 표시 → 플레이어가 혼자 추리
3. `정답 보기` 버튼 → solution(해설) 공개
- 백엔드는 문제 조회 API만 제공. 가장 단순.

### ② AI 출제자 (AI)
1. 문제 선택 → 채팅 UI 진입
2. 플레이어가 자유 질문 입력 ("주인공이 죽었나요?")
3. 백엔드가 `(scenario + solution + 대화기록 + 질문)`을 `ChatClient`로 LLM에 전달
4. 시스템 프롬프트로 **"예 / 아니오 / 상관없음 / 정답입니다" 중 하나로만 답하도록** 강제
5. "정답입니다" 판정 시 클리어 → play_history 기록
- 핵심 컴포넌트: `AiJudgeService` (LLM 호출 + 응답 정규화), 대화 세션 관리

### ③ 사람 출제자 (멀티플레이, ROOM)
1. 출제자가 방 생성 (문제 선택) → 방 코드 발급
2. 참가자가 방 코드로 입장 (닉네임)
3. 참가자가 실시간 채팅으로 질문 → 출제자가 예/아니오/상관없음 버튼으로 답
4. 출제자가 정답자 지목 + 해설 공개 → 게임 종료
- WebSocket(STOMP) + 클라이언트 STOMP.js
- 핵심 컴포넌트: `RoomService`(메모리 방 관리), WebSocket 메시지 핸들러

## 패키지 구성

```
com.turtlesoup
├─ puzzle      Puzzle 엔티티/리포지토리/조회 API, 시드 데이터
├─ classic     모드 ① (주로 프론트 + 문제 조회)
├─ ai          모드 ② — AiJudgeService, 채팅 컨트롤러, 세션
├─ room        모드 ③ — RoomService(메모리), WebSocket 설정/핸들러
└─ history     play_history 기록 + 간단 랭킹
```

프론트 정적 리소스: `index.html`(모드 선택), `classic.html`, `ai.html`, `room.html`

## 에러 처리

- AI 응답이 4지선다를 벗어나면 `AiJudgeService`에서 정규화 + 재시도(1회), 실패 시 "다시 질문해 주세요" 안내
- WebSocket 연결 끊김: 클라이언트 자동 재연결, 방은 출제자 이탈 시 종료
- 방 코드 오입력/만료: 명확한 에러 메시지
- DB 연결 실패(배포): 헬스체크 + 로깅

## 테스트

- `PuzzleRepository` / 시드 적재 단위 테스트
- `AiJudgeService` — `ChatClient` 목킹, 응답 정규화 로직 검증
- `RoomService` — 방 생성/입장/종료 로직 단위 테스트
- 통합 테스트: 문제 조회 API, AI 판정 엔드포인트(목 LLM)

## 배포

- Dockerfile (arkilo 경험 재활용) → Render/Railway 무료 티어
- DB: Supabase Postgres 무료 티어
- AI: Gemini API 키 (환경변수), `prod` 프로파일
- 환경변수: DB URL/자격증명, GEMINI_API_KEY

## 구현 순서 (설계는 한 번에, 구현은 단계별)

1. **뼈대** — Spring Boot 스켈레톤 + Puzzle 엔티티/DB(H2) + 시드 문제 + 모드 선택 페이지
2. **모드 ① CLASSIC** — 문제 표시 + 정답 공개 (바로 플레이 가능한 첫 결과물)
3. **모드 ② AI** — AiJudgeService (로컬 Ollama, prod Gemini, 프로파일 분리)
4. **모드 ③ ROOM** — WebSocket 멀티플레이
5. **배포** — Render + Supabase + Gemini 키

각 단계마다 동작하는 결과물을 확인하고 다음으로 진행한다.
