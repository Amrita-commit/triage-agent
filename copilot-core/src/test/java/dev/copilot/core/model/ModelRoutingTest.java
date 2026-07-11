package dev.copilot.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelRoutingTest {

    private static final String HAIKU = "claude-haiku-4-5-20251001";
    private static final String SONNET = "claude-sonnet-5";

    @Test
    void routesPlanningTasksToPlanningModelAndCheapTasksToCheapModel() {
        ModelRouter router = new ModelRouter(HAIKU, SONNET);
        assertThat(router.modelFor(TaskType.PLANNING)).isEqualTo(SONNET);
        assertThat(router.modelFor(TaskType.HYPOTHESIS_FORMATION)).isEqualTo(SONNET);
        assertThat(router.modelFor(TaskType.LOG_SUMMARIZATION)).isEqualTo(HAIKU);
        assertThat(router.modelFor(TaskType.DATA_EXTRACTION)).isEqualTo(HAIKU);
    }

    @Test
    void estimatesCostFromTokensAndModel() {
        TokenCostEstimator estimator = new TokenCostEstimator();
        // Sonnet: $3/1M input, $15/1M output. 1M in + 1M out => 3 + 15 = 18 USD.
        double cost = estimator.estimateUsd(SONNET, 1_000_000, 1_000_000);
        assertThat(cost).isEqualTo(18.0);
        // Unknown model uses the conservative fallback (non-zero), never silently 0.
        assertThat(estimator.estimateUsd("mystery-model", 1_000_000, 0)).isGreaterThan(0.0);
    }
}
