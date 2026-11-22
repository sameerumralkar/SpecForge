package dev.specbuilder.api.dto;

import dev.specbuilder.generator.Problem;

import java.util.List;
import java.util.Map;

/**
 * The generated project. {@code ready} is false while there are ERROR problems; the zip download
 * is refused until then. {@code endpointFiles} maps an endpoint name to the file with its logic.
 */
public record CodeView(String target, boolean ready, List<CodeFile> files, Map<String, String> endpointFiles,
                       List<Problem> problems) {

    public record CodeFile(String path, String language, String content) {
    }
}
