package dev.specbuilder.domain;

/** What kind of failure an error code describes; each has the HTTP status a code of that kind answers with by default. */
public enum ErrorCategory {
    VALIDATION(400, "A value in the request is missing or malformed"),
    BUSINESS(422, "The request is well formed but breaks a business rule"),
    NOT_FOUND(404, "Something the request refers to does not exist"),
    CONFLICT(409, "The request clashes with the current state"),
    SECURITY(403, "The caller may not do this"),
    DOWNSTREAM(503, "A service this one depends on failed"),
    TIMEOUT(504, "A service this one depends on did not answer in time"),
    PERSISTENCE(500, "Saving or loading data failed"),
    MESSAGING(500, "Publishing or receiving a message failed"),
    TECHNICAL(500, "Any other internal failure");

    private final int defaultStatus;
    private final String description;

    ErrorCategory(int defaultStatus, String description) {
        this.defaultStatus = defaultStatus;
        this.description = description;
    }

    public int defaultStatus() { return defaultStatus; }
    public String description() { return description; }
}
