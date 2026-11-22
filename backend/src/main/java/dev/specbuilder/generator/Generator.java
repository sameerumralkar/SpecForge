package dev.specbuilder.generator;

/**
 * Turns a project snapshot into files. Implementations must be deterministic: the same snapshot
 * always gives byte-for-byte the same files.
 */
public interface Generator {

    /** A stable id, e.g. "openapi". */
    String id();

    GenerationResult generate(ProjectSnapshot project);
}
