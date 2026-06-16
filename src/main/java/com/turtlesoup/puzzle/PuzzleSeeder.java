package com.turtlesoup.puzzle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PuzzleSeeder implements CommandLineRunner {

    private final PuzzleRepository repository;

    public PuzzleSeeder(PuzzleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return; // 이미 적재됨 — 멱등
        }
        repository.saveAll(List.of(
            new Puzzle(
                "바다거북 스프",
                "한 남자가 식당에서 바다거북 스프를 주문해 한 입 먹고는, 집에 돌아가 스스로 목숨을 끊었다. 왜일까?",
                "과거 조난을 당했을 때, 동료가 '바다거북 스프'라며 건넨 고기를 먹고 살아남았다. 식당에서 진짜 바다거북 스프를 맛본 순간, 그때 먹은 것이 바다거북이 아니라 죽은 동료의 살이었음을 깨달았다.",
                Difficulty.HARD, "고전,충격"),
            new Puzzle(
                "엘리베이터 속 남자",
                "한 남자가 매일 아침 1층에서 엘리베이터를 타고 출근한다. 비가 오는 날이나 다른 사람과 함께 탈 때는 자기 집인 10층까지 가지만, 그렇지 않으면 7층에서 내려 계단으로 올라간다. 왜일까?",
                "남자는 키가 매우 작아서 10층 버튼에 손이 닿지 않는다. 우산이 있는 날엔 우산으로 누르고, 다른 사람이 있으면 대신 눌러달라 부탁한다. 평소엔 손이 닿는 7층까지만 누르고 나머지는 걸어 올라간다.",
                Difficulty.NORMAL, "고전,일상"),
            new Puzzle(
                "음악이 멈추자",
                "음악이 멈추자 한 여자가 죽었다. 무슨 일이 있었을까?",
                "여자는 줄타기 곡예사였다. 눈을 가린 채 공연했고, 음악이 멈추는 것이 줄의 끝(안전한 발판)에 도달했다는 신호였다. 그러나 그날 음악이 실수로 일찍 멈췄고, 여자는 아직 줄 위인데 끝인 줄 알고 발을 내디뎌 떨어졌다.",
                Difficulty.HARD, "고전"),
            new Puzzle(
                "사막의 시체",
                "사막 한가운데에 한 남자가 죽은 채 발견되었다. 그의 손에는 부러진 성냥개비가 쥐어져 있었다. 무슨 일이 있었을까?",
                "남자는 일행과 열기구를 타고 있었다. 기구가 추락하기 시작하자 무게를 줄이려 짐을 모두 버렸지만 역부족이었다. 결국 한 명이 뛰어내려야 했고, 성냥개비 제비뽑기에서 가장 짧은 것을 뽑은 그가 뛰어내렸다.",
                Difficulty.HARD, "고전"),
            new Puzzle(
                "두 잔의 물",
                "한 여자가 식당에서 물을 한 잔 마시고 안도하며 집으로 돌아갔다. 왜일까?",
                "여자는 심한 딸꾹질을 멈추려고 식당에 들어갔다. 종업원이 갑자기 총을 꺼내 그녀를 놀라게 했고, 딸꾹질이 멈췄다. 종업원은 처음부터 그녀를 도우려 일부러 놀래킨 것이었다. 여자는 고맙다는 인사 대신 물을 마시고 안도했다.",
                Difficulty.NORMAL, "고전,따뜻함"),
            new Puzzle(
                "초인종",
                "남자는 한밤중에 모르는 사람에게서 전화를 받았다. 아무 말도 오가지 않았지만, 전화를 끊은 뒤 그는 깊이 잠들 수 있었다. 왜일까?",
                "남자는 옆방의 코골이 때문에 잠들지 못하고 있었다. 그는 옆방으로 전화를 걸었고, 코를 골던 사람이 전화를 받으려 잠에서 깨어 코골이가 멈췄다. 남자는 아무 말 없이 전화를 끊고 그 사이에 잠들었다.",
                Difficulty.NORMAL, "일상,재치")
        ));
    }
}
