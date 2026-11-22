package dev.specbuilder.generator;

/** One output file: its path inside the generated output, its text and its media type. */
public record GeneratedFile(String path, String content, String mediaType) {
}
