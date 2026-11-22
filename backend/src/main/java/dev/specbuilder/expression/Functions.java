package dev.specbuilder.expression;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The functions the shared syntax offers. Aggregates are the only place a {@code []} path may appear. */
public enum Functions {
    NOW("now", 0, 0, false, "now()", "the current date and time"),
    TODAY("today", 0, 0, false, "today()", "the current date"),
    RANDOM_UUID("randomUUID", 0, 0, false, "randomUUID()", "a new random UUID"),
    SUM("sum", 1, 1, true, "sum(items[].price * items[].quantity)", "adds up a value over the items of a list"),
    COUNT("count", 1, 1, true, "count(items[])", "how many items a list has"),
    MIN("min", 1, 1, true, "min(items[].price)", "the smallest value over the items of a list"),
    MAX("max", 1, 1, true, "max(items[].price)", "the largest value over the items of a list"),
    ANY("any", 1, 1, true, "any(items[].quantity > 10)", "true if the condition holds for at least one item"),
    ALL("all", 1, 1, true, "all(items[].quantity > 0)", "true if the condition holds for every item"),
    SIZE("size", 1, 1, false, "size(name)", "the length of a text or a list"),
    IS_EMPTY("isEmpty", 1, 1, false, "isEmpty(notes)", "true if a text or list is null or empty"),
    LOWER("lower", 1, 1, false, "lower(email)", "the text in lower case"),
    UPPER("upper", 1, 1, false, "upper(country)", "the text in upper case"),
    TRIM("trim", 1, 1, false, "trim(name)", "the text without leading and trailing spaces"),
    CONCAT("concat", 2, 10, false, "concat(firstName, ' ', lastName)", "joins values into one text"),
    COALESCE("coalesce", 2, 10, false, "coalesce(nickname, name)", "the first value that is not null"),
    ONE_OF("oneOf", 2, 20, false, "oneOf(status, 'PAID', 'SHIPPED')", "true if the first value equals any of the others");

    private static final Map<String, Functions> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(Functions::fnName, Function.identity()));

    private final String fnName;
    private final int minArgs;
    private final int maxArgs;
    private final boolean aggregate;
    private final String example;
    private final String description;

    Functions(String fnName, int minArgs, int maxArgs, boolean aggregate, String example, String description) {
        this.fnName = fnName;
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
        this.aggregate = aggregate;
        this.example = example;
        this.description = description;
    }

    public static Functions byName(String name) {
        return BY_NAME.get(name);
    }

    public String fnName() { return fnName; }
    public int minArgs() { return minArgs; }
    public int maxArgs() { return maxArgs; }
    public boolean aggregate() { return aggregate; }
    public String example() { return example; }
    public String description() { return description; }
}
