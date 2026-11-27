package dev.specbuilder.capabilities;

import java.util.List;
import java.util.UUID;

/**
 * One capability in an import draft. {@code key} identifies it within the draft, {@code parentKey} places it
 * (null: top level of the import). {@code apis} are endpoint names. {@code resolution} says what applying does:
 * create a new capability, merge into the existing {@code mergeTargetId}, or skip it and everything below it.
 * {@code sourceRow} is the row (or item) of the file it came from.
 */
public record ImportNode(String key, String parentKey, String name, String code, String description, List<String> apis,
                         Resolution resolution, java.util.UUID mergeTargetId, Integer sourceRow) {

    public enum Resolution { CREATE, MERGE, SKIP }

    public ImportNode with(Resolution resolution, UUID mergeTargetId) {
        return new ImportNode(key, parentKey, name, code, description, apis, resolution, mergeTargetId, sourceRow);
    }
}
