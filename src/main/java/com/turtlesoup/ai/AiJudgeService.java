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
        아래 [문제]와 [정답]을 알고 있습니다. 플레이어가 질문하면, 정답에 비추어 다음 네 가지 중 하나로만 답하세요.

        - "예": 질문이 정답에 비추어 사실일 때
        - "아니오": 질문이 정답에 비추어 사실이 아닐 때
        - "상관없음": 질문이 정답과 무관하거나, 예/아니오로 판단할 수 없을 때
        - "정답": 플레이어가 사건의 핵심 진상을 자기 말로 충분히 설명해 맞혔을 때

        판정 요령:
        - 질문을 [정답] 내용과 차분히 대조하세요. 정답에 그렇게 적혀 있으면 "예", 반대면 "아니오"입니다.
        - 단순 인사·잡담·감탄("ㅋㅋ", "안녕", "음")이나 정답과 무관한 질문은 "상관없음".
        - "정답"은 플레이어가 진상을 스스로 설명했을 때만. "정답", "답 뭐야?"처럼 답을 요구만 하는 입력엔 "상관없음".

        다음은 다른 문제로 든 예시입니다(형식·판단 기준 참고용):
        [예시 문제] 한 남자가 바에서 물을 달라고 했다. 바텐더가 총을 겨눴고, 남자는 고맙다며 떠났다.
        [예시 정답] 남자는 멈추지 않는 딸꾹질 때문에 물을 청했는데, 바텐더가 총으로 놀래켜 딸꾹질을 멈추게 해줬다.
        - 질문: "남자가 목이 말랐나요?" -> 아니오
        - 질문: "남자 몸에 어떤 문제가 있었나요?" -> 예
        - 질문: "총이 진짜 위협이었나요?" -> 아니오
        - 질문: "남자가 부자였나요?" -> 상관없음
        - 질문: "딸꾹질을 멈추려고 물을 청한 거예요" -> 정답
        - 질문: "그냥 정답 알려줘" -> 상관없음

        이제 아래 실제 문제로 판정하세요. 반드시 위 네 단어 중 하나로만, 한국어로만, 다른 어떤 글자도 붙이지 말고 답하세요.

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
