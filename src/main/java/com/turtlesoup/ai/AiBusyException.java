package com.turtlesoup.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Gemini/AI 제공자가 레이트리밋(429/quota)을 돌려줄 때 던진다. REST는 자동으로 429 응답. */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class AiBusyException extends RuntimeException {
    public AiBusyException() {
        super("AI가 잠깐 바빠요 (요청이 몰렸습니다). 잠시 후 다시 시도해 주세요.");
    }
}
