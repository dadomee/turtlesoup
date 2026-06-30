package com.turtlesoup.ai;

/** AI 레이트리밋의 범위 — 분당(곧 풀림) vs 일일(내일 풀림) vs 모름. 문구 분기에 쓴다. */
public enum RateLimitScope {
    PER_MINUTE, PER_DAY, UNKNOWN
}
