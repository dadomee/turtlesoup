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
        - 3단계: 정답에 꽤 근접하게. 단, 정답 문장을 그대로 말하지는 마세요.

        규칙:
        - 힌트 한두 문장만 출력하세요. "힌트:" 같은 접두어나 다른 말을 붙이지 마세요.
        - 정답을 직접 누설하지 마세요.

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
        - 반드시 위 네 단어 중 하나로만 답하세요. 다른 설명, 문장, 이모지를 절대 붙이지 마세요.
        - 정답을 직접 누설하지 마세요.

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

    public Verdict judge(Long puzzleId, String question) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
            .orElseThrow(() -> new PuzzleNotFoundException(puzzleId));

        String system = String.format(SYSTEM_PROMPT, puzzle.getScenario(), puzzle.getSolution());

        String raw = chatClient.prompt()
            .system(system)
            .user("[플레이어 질문] " + question)
            .call()
            .content();

        return VerdictParser.parse(raw);
    }

    public String hint(Long puzzleId, int n) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
            .orElseThrow(() -> new PuzzleNotFoundException(puzzleId));
        String system = String.format(HINT_PROMPT, n, puzzle.getScenario(), puzzle.getSolution());
        return chatClient.prompt()
            .system(system)
            .user("힌트를 주세요.")
            .call()
            .content();
    }
}
