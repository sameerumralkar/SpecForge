package dev.specbuilder.domain;

import jakarta.persistence.*;

@Embeddable
public class ResponseDefinition {

    /** A three-digit HTTP status code, or "default". */
    @Column(name = "status_code", nullable = false, length = 7)
    private String statusCode;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payload_id")
    private Payload payload;

    public ResponseDefinition() {
    }

    public ResponseDefinition(String statusCode, String description, Payload payload) {
        this.statusCode = statusCode;
        this.description = description;
        this.payload = payload;
    }

    public ResponseDefinition copy() {
        return new ResponseDefinition(statusCode, description, payload);
    }

    public String getStatusCode() { return statusCode; }
    public String getDescription() { return description; }
    public Payload getPayload() { return payload; }
}
