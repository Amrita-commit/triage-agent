package dev.copilot.core.model;

/**
 * The kind of work an LLM call performs. Drives model routing: cheap/high-volume extraction and
 * summarization go to a small model (Haiku); planning and hypothesis formation go to a stronger
 * model (Sonnet). See {@link ModelRouter}.
 */
public enum TaskType {
    /** Deciding what to investigate next and which tools to call — needs strong reasoning. */
    PLANNING(ModelTier.PLANNING),
    /** Forming/refining a root-cause hypothesis from evidence — needs strong reasoning. */
    HYPOTHESIS_FORMATION(ModelTier.PLANNING),
    /** Condensing large/noisy tool output (e.g. log dumps) — cheap and high volume. */
    LOG_SUMMARIZATION(ModelTier.CHEAP),
    /** Pulling a specific value/field out of tool output — cheap and high volume. */
    DATA_EXTRACTION(ModelTier.CHEAP);

    /** Coarse capability tier a task maps to. */
    public enum ModelTier {
        CHEAP,
        PLANNING
    }

    private final ModelTier tier;

    TaskType(ModelTier tier) {
        this.tier = tier;
    }

    public ModelTier tier() {
        return tier;
    }
}
