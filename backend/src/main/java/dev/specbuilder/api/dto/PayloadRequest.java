package dev.specbuilder.api.dto;

import dev.specbuilder.domain.PayloadRole;

import java.util.List;

/** {@code role} may be left out: a new payload is then a STRUCTURE, an updated one keeps its role. */
public record PayloadRequest(String name, String description, String example, List<FieldDto> fields, PayloadRole role) {

    public PayloadRequest(String name, String description, String example, List<FieldDto> fields) {
        this(name, description, example, fields, null);
    }
}
