package dev.specbuilder.service;

import dev.specbuilder.api.dto.SpecView;
import dev.specbuilder.api.dto.SpecsView;
import dev.specbuilder.generator.*;
import dev.specbuilder.generator.spec.AsyncApiGenerator;
import dev.specbuilder.generator.spec.OpenApiGenerator;
import dev.specbuilder.generator.spec.SpecValidator;
import dev.specbuilder.generation.GenerationService;
import dev.specbuilder.generation.GenerationSnapshot;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Generates and validates the OpenAPI and AsyncAPI documents of a project. */
@Service
public class SpecService {

    /** Zip entries get a fixed timestamp so the same project always gives the same bytes. */
    private static final FileTime FIXED_TIME = FileTime.fromMillis(315532800000L); // 1980-01-01

    private final SnapshotService snapshots;
    private final OpenApiGenerator openApi;
    private final AsyncApiGenerator asyncApi;
    private final SpecValidator validator;
    private final GenerationService generations;

    public SpecService(SnapshotService snapshots, OpenApiGenerator openApi, AsyncApiGenerator asyncApi, SpecValidator validator,
                       GenerationService generations) {
        this.snapshots = snapshots;
        this.openApi = openApi;
        this.asyncApi = asyncApi;
        this.validator = validator;
        this.generations = generations;
    }

    /** openapi.yaml/json and asyncapi.yaml/json by path, for those the project has. */
    public java.util.Map<String, String> currentFiles(UUID projectId) {
        ProjectSnapshot project = snapshots.load(projectId);
        java.util.Map<String, String> files = new java.util.TreeMap<>();
        openApi.generate(project).files().forEach(f -> files.put(f.path(), f.content()));
        asyncApi.generate(project).files().forEach(f -> files.put(f.path(), f.content()));
        return files;
    }

    public SpecsView specs(UUID projectId) {
        ProjectSnapshot project = snapshots.load(projectId);
        return new SpecsView(view(OpenApiGenerator.ID, openApi(project)), view(AsyncApiGenerator.ID, asyncApi(project)));
    }

    /** One generated file by name (openapi.yaml, asyncapi.json, …), or null if the project has none. */
    public GeneratedFile file(UUID projectId, String fileName) {
        ProjectSnapshot project = snapshots.load(projectId);
        GenerationResult result = fileName.startsWith(OpenApiGenerator.ID) ? openApi(project) : asyncApi(project);
        return result.file(fileName);
    }

    public byte[] zip(UUID projectId) {
        ProjectSnapshot project = snapshots.load(projectId);
        List<GeneratedFile> files = new ArrayList<>(openApi(project).files());
        files.addAll(asyncApi(project).files());
        files.sort(Comparator.comparing(GeneratedFile::path));
        java.util.Map<String, String> downloaded = new java.util.TreeMap<>();
        files.forEach(f -> downloaded.put(f.path(), f.content()));
        generations.record(projectId, GenerationSnapshot.Kind.SPECS, GenerationSnapshot.Reason.DOWNLOAD, downloaded);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (GeneratedFile f : files) {
                ZipEntry entry = new ZipEntry(f.path());
                entry.setLastModifiedTime(FIXED_TIME);
                zip.putNextEntry(entry);
                zip.write(f.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public String projectSlug(UUID projectId) {
        return snapshots.load(projectId).name().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    GenerationResult openApi(ProjectSnapshot project) {
        GenerationResult result = openApi.generate(project);
        if (result.files().isEmpty()) {
            return result;
        }
        return result.withProblems(validator.validateOpenApi(result.file("openapi.json").content(), result.file("openapi.yaml").content()));
    }

    GenerationResult asyncApi(ProjectSnapshot project) {
        GenerationResult result = asyncApi.generate(project);
        if (result.files().isEmpty()) {
            return result;
        }
        return result.withProblems(validator.validateAsyncApi(result.file("asyncapi.json").content()));
    }

    private static SpecView view(String id, GenerationResult result) {
        GeneratedFile yaml = result.file(id + ".yaml");
        GeneratedFile json = result.file(id + ".json");
        return new SpecView(id, yaml != null, yaml == null ? null : yaml.content(), json == null ? null : json.content(),
                !result.hasErrors(), result.problems());
    }
}
