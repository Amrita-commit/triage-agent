package dev.copilot.core.model;

import dev.copilot.core.model.TaskType.ModelTier;

/**
 * Routes each {@link TaskType} to a concrete model id. Model routing is a first-class feature: it
 * keeps cheap, high-volume steps on a small model while reserving the stronger model for planning
 * and hypothesis formation, which materially reduces cost per incident.
 */
public class ModelRouter {

    private final String cheapModel;
    private final String planningModel;

    public ModelRouter(String cheapModel, String planningModel) {
        if (cheapModel == null || cheapModel.isBlank() || planningModel == null || planningModel.isBlank()) {
            throw new IllegalArgumentException("both cheapModel and planningModel must be set");
        }
        this.cheapModel = cheapModel;
        this.planningModel = planningModel;
    }

    /** Returns the model id to use for the given task type. */
    public String modelFor(TaskType taskType) {
        return taskType.tier() == ModelTier.PLANNING ? planningModel : cheapModel;
    }

    public String cheapModel() {
        return cheapModel;
    }

    public String planningModel() {
        return planningModel;
    }
}
