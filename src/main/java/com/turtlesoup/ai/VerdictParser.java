package com.turtlesoup.ai;

public final class VerdictParser {

    private VerdictParser() {
    }

    public static Verdict parse(String raw) {
        if (raw == null) {
            return Verdict.UNKNOWN;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return Verdict.UNKNOWN;
        }
        if (s.contains("아니") || s.contains("아닙")) {
            return Verdict.NO;
        }
        if (s.contains("정답")) {
            return Verdict.CORRECT;
        }
        if (s.contains("상관") || s.contains("관계")) {
            return Verdict.IRRELEVANT;
        }
        if (s.contains("예") || s.contains("네")) {
            return Verdict.YES;
        }
        return Verdict.UNKNOWN;
    }
}
