package com.turtlesoup.ai;

public final class VerdictParser {

    private VerdictParser() {
    }

    public static Verdict parse(String raw) {
        if (raw == null) {
            return Verdict.UNKNOWN;
        }
        // 공백·구두점 제거로 "예." "상관 없음" 등을 정규화
        String s = raw.replaceAll("[\\s\\p{Punct}…]+", "");
        if (s.isEmpty()) {
            return Verdict.UNKNOWN;
        }
        // 부정을 정답보다 먼저 검사 → "정답이 아닙니다"를 NO로 처리
        // ("아닙니다"는 '아니' substring을 포함하지 않으므로 '아닙'도 검사)
        if (s.contains("아니") || s.contains("아닙")) {
            return Verdict.NO;
        }
        if (s.contains("정답")) {
            return Verdict.CORRECT;
        }
        // "없"까지 포함해 "인간관계" 같은 합성어 오탐을 방지
        if (s.contains("상관없") || s.contains("관계없")) {
            return Verdict.IRRELEVANT;
        }
        // 예/네는 짧아 합성어(예외, 네트워크)에 오탐될 수 있어 정확 일치만 인정
        if (s.equals("예") || s.equals("네") || s.equals("넵") || s.equals("응")) {
            return Verdict.YES;
        }
        return Verdict.UNKNOWN;
    }
}
