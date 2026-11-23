package dev.specbuilder.api.dto;

import java.util.List;

/** The text of one or more OpenAPI / AsyncAPI files, and optional overrides for the new project. */
public record SpecImportRequest(List<String> documents, String name, String basePackage) {
}
