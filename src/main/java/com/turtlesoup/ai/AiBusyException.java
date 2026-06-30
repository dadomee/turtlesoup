package com.turtlesoup.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Gemini/AI 제공자가 레이트리밋(429/quota)을 돌려줄 때 던진다. REST는 자동으로 429 응답. */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class AiBusyException extends RuntimeException {

    /** 어떤 요청에서 났는지 — 문구 말미(힌트 차감 안내)가 달라진다. */
    public enum Context { ASK, HINT }

    private final RateLimitScope scope;

    public AiBusyException(Context context, RateLimitScope scope) {
        super(message(context, scope));
        this.scope = scope;
    }

    public RateLimitScope getScope() {
        return scope;
    }

    // 상황별 귀여운 안내 문구. 분당=곧 풀림 / 일일=내일 / 모름=중립.
    private static String message(Context context, RateLimitScope scope) {
        boolean hint = context == Context.HINT;
        String again = hint ? "눌러" : "물어봐";
        String tail = hint ? " (힌트는 안 차감됐어요) 🐢" : " 🐢";
        return switch (scope) {
            case PER_DAY -> "🤖 오늘은 AI가 열일 다 했나 봐요…! 😴 무료 한도를 다 써서 내일 다시 만나요"
                    + tail + "💤";
            case PER_MINUTE -> "🤖 AI가 지금 너무 열일하는 중이에요! 🫧 조금 이따 다시 " + again + " 주세요" + tail;
            case UNKNOWN -> "🤖 AI가 잠깐 바빠요! 🫧 조금 이따 다시 " + again + " 주세요" + tail;
        };
    }
}
