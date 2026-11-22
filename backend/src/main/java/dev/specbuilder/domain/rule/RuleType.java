package dev.specbuilder.domain.rule;

public enum RuleType {
    VALIDATE("Validate", ValidateConfig.class),
    TRANSFORM("Transform / map", TransformConfig.class),
    ENRICH("Enrich", EnrichConfig.class),
    CONDITIONAL_ROUTE("Conditional route", ConditionalRouteConfig.class),
    PERSIST("Persist", PersistConfig.class),
    PUBLISH_EVENT("Publish event", PublishEventConfig.class),
    SPLIT_AGGREGATE("Split / aggregate", SplitAggregateConfig.class),
    ERROR_HANDLING("Error handling", ErrorHandlingConfig.class);

    private final String label;
    private final Class<? extends RuleConfig> configClass;

    RuleType(String label, Class<? extends RuleConfig> configClass) {
        this.label = label;
        this.configClass = configClass;
    }

    public String label() { return label; }
    public Class<? extends RuleConfig> configClass() { return configClass; }
}
