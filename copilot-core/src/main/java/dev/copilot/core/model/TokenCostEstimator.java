package dev.copilot.core.model;

import java.util.Map;

/**
 * Estimates the USD cost of an LLM call from token counts, using a per-model price table
 * (USD per 1M tokens, input and output). Prices are approximate and configurable; unknown models
 * fall back to a conservative default so cost is never silently reported as zero.
 */
public class TokenCostEstimator {

    /** USD per 1,000,000 tokens for a model, split into input and output rates. */
    public record Price(double inputPerMillion, double outputPerMillion) {}

    // Approximate public list prices for the Claude models used by this project.
    private static final Map<String, Price> DEFAULT_PRICES = Map.of(
            "claude-haiku-4-5-20251001", new Price(1.0, 5.0),
            "claude-sonnet-5", new Price(3.0, 15.0),
            "claude-opus-4-8", new Price(15.0, 75.0));

    private static final Price FALLBACK = new Price(3.0, 15.0);

    private final Map<String, Price> prices;

    public TokenCostEstimator() {
        this(DEFAULT_PRICES);
    }

    public TokenCostEstimator(Map<String, Price> prices) {
        this.prices = Map.copyOf(prices);
    }

    /** Estimated USD cost for a single call. Null token counts are treated as zero. */
    public double estimateUsd(String model, Integer inputTokens, Integer outputTokens) {
        Price p = prices.getOrDefault(model, FALLBACK);
        double in = (inputTokens == null ? 0 : inputTokens) / 1_000_000.0 * p.inputPerMillion();
        double out = (outputTokens == null ? 0 : outputTokens) / 1_000_000.0 * p.outputPerMillion();
        return in + out;
    }
}
