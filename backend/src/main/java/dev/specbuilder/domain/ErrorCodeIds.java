package dev.specbuilder.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Error-code links are stored as id strings; these keep them distinct and in order. */
final class ErrorCodeIds {

    private ErrorCodeIds() {
    }

    static List<UUID> read(List<String> stored) {
        return stored == null ? List.of() : stored.stream().map(UUID::fromString).distinct().toList();
    }

    static List<String> write(List<UUID> ids) {
        return ids == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(ids).stream().map(UUID::toString).toList());
    }
}
