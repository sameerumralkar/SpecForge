package dev.specbuilder.generator.code;

import java.util.List;
import java.util.Map;

/**
 * A code target's manifest.yml: which templates to render, once or per item of a model
 * collection, and the versions and reserved names they rely on.
 */
public record TargetManifest(String id, String name, Map<String, String> properties, List<String> reservedNames,
                             List<FileEntry> files) {

    /**
     * @param each  name of a model collection to render once per item (dtos, groups, routes, entities, clients)
     * @param items literal items to render once each, instead of a model collection
     * @param when  a FreeMarker condition; the file is skipped when it is false
     * @param copy  copy the file as it is instead of rendering it
     */
    public record FileEntry(String template, String output, String each, List<Map<String, Object>> items, String when,
                            boolean copy, boolean executable, boolean primary) {
    }
}
