package com.turtlesoup.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictParserTest {

    @Test
    void parsesYes() {
        assertThat(VerdictParser.parse("예")).isEqualTo(Verdict.YES);
        assertThat(VerdictParser.parse("네")).isEqualTo(Verdict.YES);
    }

    @Test
    void parsesNo() {
        assertThat(VerdictParser.parse("아니오")).isEqualTo(Verdict.NO);
        assertThat(VerdictParser.parse("아니요")).isEqualTo(Verdict.NO);
    }

    @Test
    void parsesIrrelevant() {
        assertThat(VerdictParser.parse("상관없음")).isEqualTo(Verdict.IRRELEVANT);
        assertThat(VerdictParser.parse("관계없음")).isEqualTo(Verdict.IRRELEVANT);
    }

    @Test
    void parsesCorrect() {
        assertThat(VerdictParser.parse("정답")).isEqualTo(Verdict.CORRECT);
        assertThat(VerdictParser.parse("정답입니다")).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void noTakesPriorityOverCorrectWhenBothPresent() {
        // "정답이 아닙니다" 처럼 둘 다 포함되면 부정으로 본다
        assertThat(VerdictParser.parse("정답이 아닙니다")).isEqualTo(Verdict.NO);
    }

    @Test
    void trimsAndIgnoresSurroundingText() {
        assertThat(VerdictParser.parse("  예.  ")).isEqualTo(Verdict.YES);
    }

    @Test
    void unknownWhenNoKeyword() {
        assertThat(VerdictParser.parse("글쎄요")).isEqualTo(Verdict.UNKNOWN);
        assertThat(VerdictParser.parse("")).isEqualTo(Verdict.UNKNOWN);
        assertThat(VerdictParser.parse(null)).isEqualTo(Verdict.UNKNOWN);
    }
}
