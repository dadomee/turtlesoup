package com.turtlesoup.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitClassifyTest {

    @Test
    void freeTierDailyQuotaIsPerDay() {
        // 실제 Gemini 무료 일일 한도 초과 메시지(메트릭 generate_content_free_tier_requests)
        Throwable e = new RuntimeException(
            "429 You exceeded your current quota. Quota exceeded for metric: " +
            "generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, " +
            "model: gemini-2.5-flash. Please retry in 44s. status: RESOURCE_EXHAUSTED");
        assertThat(AiJudgeService.classifyRateLimit(e)).isEqualTo(RateLimitScope.PER_DAY);
    }

    @Test
    void perMinuteQuotaIsPerMinute() {
        Throwable e = new RuntimeException(
            "429 RESOURCE_EXHAUSTED quota GenerateRequestsPerMinutePerProjectPerModel-FreeTier exceeded");
        assertThat(AiJudgeService.classifyRateLimit(e)).isEqualTo(RateLimitScope.PER_MINUTE);
    }

    @Test
    void genericRateLimitIsUnknown() {
        Throwable e = new RuntimeException("429 Too Many Requests: rate limit reached");
        assertThat(AiJudgeService.classifyRateLimit(e)).isEqualTo(RateLimitScope.UNKNOWN);
    }

    @Test
    void nonRateLimitErrorIsNull() {
        Throwable e = new RuntimeException("Connection refused");
        assertThat(AiJudgeService.classifyRateLimit(e)).isNull();
    }

    @Test
    void detectsThroughCauseChain() {
        Throwable root = new RuntimeException("free_tier_requests quota exceeded");
        Throwable wrapped = new RuntimeException("AI call failed", root);
        assertThat(AiJudgeService.classifyRateLimit(wrapped)).isEqualTo(RateLimitScope.PER_DAY);
    }
}
