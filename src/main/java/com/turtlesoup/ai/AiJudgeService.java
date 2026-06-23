package com.turtlesoup.ai;

import com.turtlesoup.puzzle.Puzzle;
import com.turtlesoup.puzzle.PuzzleNotFoundException;
import com.turtlesoup.puzzle.PuzzleRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiJudgeService {

    private static final String HINT_PROMPT = """
        당신은 '바다거북스프'(수평적 사고 추리 게임)의 출제자입니다.
        아래 [문제]와 [정답]을 알고 있습니다. 플레이어에게 힌트를 딱 하나만 주세요.

        힌트 단계는 %d/3입니다.
        - 1단계: 막연한 방향만 살짝 (관점/주제 환기). 거의 알려주지 마세요.
        - 2단계: 조금 더 구체적으로 좁혀 주세요.
        - 3단계(마지막): 정답 직전까지 강하게 좁혀 주되, 사건의 진상(정답)을 절대 직접 말하지 마세요. 마지막 추론은 플레이어의 몫으로 남겨 두세요.

        규칙:
        - 반드시 한국어로만 작성하세요. 중국어·영어 등 다른 언어의 글자나 단어를 절대 섞지 마세요.
        - 힌트 한두 문장만 출력하세요. "힌트:" 같은 접두어나 다른 말을 붙이지 마세요.
        - 어떤 단계에서도 정답(진상)을 그대로 말하지 마세요.

        [문제]
        %s

        [정답]
        %s
        """;

    private static final String SYSTEM_PROMPT = """
        당신은 '바다거북스프'(수평적 사고 추리 게임)의 출제자입니다.
        아래 [문제]와 [정답]을 알고 있습니다. 플레이어가 질문하면, 정답에 비추어 아래 네 가지 중 하나로만 답하세요.

        - "예": 질문 내용이 정답에 비추어 맞을 때
        - "아니오": 질문 내용이 정답에 비추어 틀릴 때
        - "상관없음": 질문이 정답과 관련이 없거나 판단할 수 없을 때
        - "정답": 플레이어가 사건의 핵심 진상을 충분히 맞혔을 때

        규칙:
        - 반드시 위 네 단어 중 하나로만, 한국어로만 답하세요. 다른 설명·문장·이모지·다른 언어를 절대 붙이지 마세요.
        - 정답을 직접 누설하지 마세요.
        - "정답"은 플레이어가 사건의 핵심 진상을 자기 말로 충분히 설명했을 때만 주세요. "정답", "답 알려줘", "정답이 뭐야?"처럼 진상을 설명하지 않고 답만 요구하는 입력에는 절대 "정답"을 주지 말고 "상관없음"으로 답하세요.

        [문제]
        %s

        [정답]
        %s
        """;

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
            .user("[플레이어 질문] " + question)
            .call()
            .content();
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
