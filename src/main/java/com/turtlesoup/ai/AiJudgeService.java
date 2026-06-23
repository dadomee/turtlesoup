package com.turtlesoup.ai;

import com.turtlesoup.puzzle.Puzzle;
import com.turtlesoup.puzzle.PuzzleNotFoundException;
import com.turtlesoup.puzzle.PuzzleRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiJudgeService {

    private static final String HINT_PROMPT = """
        당신은 '바다거북스프'(수평적 사고 추리 게임)의 출제자입니다.
        아래 [문제]와 [정답]을 알고 있습니다. 플레이어에게 도움이 될 힌트를 딱 하나만 주세요.

        힌트 단계는 %d/3입니다. 단계가 올라갈수록 더 구체적으로 좁혀 주세요.
        - 1단계: 어디에 주목해야 할지 방향만 살짝 (관점·주제 환기). 거의 알려주지 마세요.
        - 2단계: 핵심 요소를 조금 더 구체적으로 좁혀 주세요.
        - 3단계(마지막): 정답 직전까지 강하게 좁혀 주되, 진상(정답)을 절대 직접 말하지 마세요. 마지막 한 걸음은 플레이어 몫으로 남기세요.

        좋은 힌트 예시(다른 문제 기준):
        [예시 정답] 남자는 멈추지 않는 딸꾹질 때문에 물을 청했고, 바텐더가 총으로 놀래켜 멈추게 해줬다.
        - 1단계: "남자가 진짜 원한 건 '물' 그 자체가 아니었어요. 그가 겪던 곤란에 주목하세요."
        - 2단계: "그 곤란은 갑작스럽고 본인 의지로는 잘 멈추지 않는 종류였어요."
        - 3단계: "바텐더는 위협한 게 아니라 남자를 '깜짝' 도와준 거예요. 그 충격이 곤란을 단번에 끝냈죠."

        규칙:
        - 반드시 한국어로만 작성하세요. 중국어·영어 등 다른 언어 글자를 절대 섞지 마세요.
        - 힌트 한두 문장만 출력하세요. "힌트:" 같은 접두어나 군더더기를 붙이지 마세요.
        - 어떤 단계에서도 정답(진상)을 그대로 말하지 마세요.

        [문제]
        %s

        [정답]
        %s
        """;

    private static final String SYSTEM_PROMPT = """
        당신은 '바다거북스프'(수평적 사고 추리 게임)의 출제자입니다.
        당신만 [정답](사건의 진상)을 알고, 플레이어는 예/아니오 질문으로 진상을 좁혀 갑니다.
        플레이어의 입력을 [정답]과 대조해, 먼저 속으로 근거를 따진 뒤 아래 다섯 중 하나로 판정하세요.

        - "예": 질문이 [정답]에 비추어 사실이다.
        - "아니오": 질문이 [정답]에 비추어 사실이 아니다.
        - "상관없음": 질문이 [정답]과 무관하거나 진상을 푸는 데 중요하지 않다.
        - "모호": 질문이 너무 막연하거나 두 가지가 섞여 예/아니오로 답하기 어렵다(더 구체적으로 물으라는 신호).
        - "정답": 플레이어가 [정답]의 핵심 인과(왜 그 일이 벌어졌는가)를 자기 말로 1~2개 짚어 진상을 맞혔다.

        판정 기준:
        - 부정문("~하지 않았나요?")은 의미를 뒤집어 신중히 판단하세요.
        - 이미 [문제]에 드러난 사실을 물으면 "예".
        - "정답"은 핵심 인과를 스스로 설명했을 때만 줍니다. 이름·숫자 같은 지엽적 디테일은 필요 없습니다. "정답 알려줘"처럼 답을 요구만 하면 "정답"을 주지 말고 "상관없음".
        - 방향만 맞고 핵심 인과를 아직 못 짚었으면 "정답"이 아니라 "예"로 격려만 하세요.
        - 인사·잡담·감탄은 "상관없음".

        출력 형식: 반드시 아래 JSON 한 줄로만, 한국어로만 답하세요. 코드블록·다른 문장·다른 언어 금지.
        빠른 응답을 위해 "근거"는 4~6단어로 아주 짧게 쓰세요.
        {"근거": "4~6단어", "판정": "예 또는 아니오 또는 상관없음 또는 모호 또는 정답"}

        예시(다른 문제):
        [예시 문제] 한 남자가 바에서 물을 달라고 했다. 바텐더가 총을 겨눴고, 남자는 고맙다며 떠났다.
        [예시 정답] 남자는 멈추지 않는 딸꾹질 때문에 물을 청했는데, 바텐더가 총으로 놀래켜 딸꾹질을 멈추게 해줬다.
        입력: "남자가 목이 말랐나요?" -> {"근거": "물은 핑계", "판정": "아니오"}
        입력: "총이 진짜 위협이었나요?" -> {"근거": "위협 아닌 도움", "판정": "아니오"}
        입력: "남자가 부자였나요?" -> {"근거": "재산은 무관", "판정": "상관없음"}
        입력: "남자에게 무슨 일이 있었나요?" -> {"근거": "너무 막연함", "판정": "모호"}
        입력: "남자는 몸이 불편했고 바텐더가 도와준 거죠?" -> {"근거": "방향만 맞고 핵심 미흡", "판정": "예"}
        입력: "딸꾹질을 멈추려고 물을 청했고 총소리에 놀라 멎은 거예요" -> {"근거": "핵심 정확히 설명", "판정": "정답"}
        입력: "그냥 정답 알려줘" -> {"근거": "답만 요구함", "판정": "상관없음"}

        이제 아래 실제 문제로 판정하세요.

        [문제]
        %s

        [정답]
        %s
        """;

    // 모델 응답에서 JSON "판정" 값을 뽑아낸다 (없으면 전체 텍스트로 폴백)
    private static final Pattern VERDICT_FIELD = Pattern.compile("판정\"?\\s*:\\s*\"?([^\"}\\n,]+)");

    private final ChatClient chatClient;
    private final PuzzleRepository puzzleRepository;

    public AiJudgeService(ChatClient chatClient, PuzzleRepository puzzleRepository) {
        this.chatClient = chatClient;
        this.puzzleRepository = puzzleRepository;
    }

    private Puzzle find(Long id) {
        return puzzleRepository.findById(id).orElseThrow(() -> new PuzzleNotFoundException(id));
    }

    public Verdict judge(Long puzzleId, String question) {
        Puzzle p = find(puzzleId);
        return judge(p.getScenario(), p.getSolution(), question);
    }

    public Verdict judge(String scenario, String solution, String question) {
        String system = String.format(SYSTEM_PROMPT, scenario, solution);
        String raw = chatClient.prompt()
            .system(system)
            .user("[플레이어 입력] " + question)
            .call()
            .content();
        return extractVerdict(raw);
    }

    // 모델이 {"근거":...,"판정":"예"} 형태로 답하면 '판정'만 추출해 파싱.
    // JSON이 깨졌으면 전체 텍스트에서 키워드로 폴백 판정한다.
    Verdict extractVerdict(String raw) {
        if (raw == null || raw.isBlank()) return Verdict.UNKNOWN;
        Matcher m = VERDICT_FIELD.matcher(raw);
        if (m.find()) return VerdictParser.parse(m.group(1).trim());
        return VerdictParser.parse(raw);
    }

    public String hint(Long puzzleId, int n) {
        Puzzle p = find(puzzleId);
        return hint(p.getScenario(), p.getSolution(), n);
    }

    public String hint(String scenario, String solution, int n) {
        String system = String.format(HINT_PROMPT, n, scenario, solution);
        return chatClient.prompt()
            .system(system)
            .user("힌트를 주세요.")
            .call()
            .content();
    }
}
