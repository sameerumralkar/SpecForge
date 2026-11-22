package dev.specbuilder.service;

import dev.specbuilder.api.dto.CodeView;
import dev.specbuilder.api.dto.RuleChainView;
import dev.specbuilder.generator.GeneratedFile;
import dev.specbuilder.generator.Problem;
import dev.specbuilder.generator.ProjectSnapshot;
import dev.specbuilder.generator.SnapshotService;
import dev.specbuilder.generator.code.CodeGenerator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Generates the Maven project of a project, for preview and as a zip. */
@Service
public class CodeService {

    private static final FileTime FIXED_TIME = FileTime.fromMillis(315532800000L); // 1980-01-01, for byte-identical zips

    private final SnapshotService snapshots;
    private final RuleService rules;
    private final CodeGenerator generator;

    public CodeService(SnapshotService snapshots, RuleService rules, CodeGenerator generator) {
        this.snapshots = snapshots;
        this.rules = rules;
        this.generator = generator;
    }

    public CodeView preview(UUID projectId) {
        ProjectSnapshot project = snapshots.load(projectId);
        CodeGenerator.Output output = generator.render(project);
        List<Problem> problems = new ArrayList<>(ruleProblems(project));
        problems.addAll(output.result().problems());
        boolean ready = problems.stream().noneMatch(p -> p.severity() == Problem.Severity.ERROR);
        List<CodeView.CodeFile> files = output.result().files().stream()
                .map(f -> new CodeView.CodeFile(f.path(), language(f.path()), f.content())).toList();
        return new CodeView(output.targetName(), ready, files, output.endpointFiles(), problems);
    }

    /** The project as a zip with one top folder; refused with {@link CodeNotReadyException} while there are errors. */
    public byte[] zip(UUID projectId) {
        ProjectSnapshot project = snapshots.load(projectId);
        CodeGenerator.Output output = generator.render(project);
        List<Problem> problems = new ArrayList<>(ruleProblems(project));
        problems.addAll(output.result().problems());
        List<Problem> errors = problems.stream().filter(p -> p.severity() == Problem.Severity.ERROR).toList();
        if (!errors.isEmpty()) {
            throw new CodeNotReadyException(errors);
        }
        String folder = slug(project.name()) + "/";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
            for (GeneratedFile f : output.result().files()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(folder + f.path());
                entry.setLastModifiedTime(FIXED_TIME);
                entry.setUnixMode(CodeGenerator.EXECUTABLE.equals(f.mediaType()) ? 0100755 : 0100644);
                zip.putArchiveEntry(entry);
                zip.write(f.content().getBytes(StandardCharsets.UTF_8));
                zip.closeArchiveEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static String slug(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "project" : s;
    }

    /** Rule-chain warnings would generate code that doesn't line up, so they block generation. */
    private List<Problem> ruleProblems(ProjectSnapshot project) {
        List<Problem> problems = new ArrayList<>();
        for (ProjectSnapshot.EndpointSnapshot e : project.endpoints()) {
            RuleChainView chain = rules.chain(project.id(), e.id());
            chain.warnings().forEach(w -> problems.add(Problem.error("rules", e.name() + " / " + w.ruleName(), w.message())));
        }
        return problems;
    }

    private static String language(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".xml")) return "xml";
        if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties")) return "yaml";
        if (name.endsWith(".md")) return "markdown";
        if (name.equals("mvnw")) return "shell";
        return "plaintext";
    }
}
