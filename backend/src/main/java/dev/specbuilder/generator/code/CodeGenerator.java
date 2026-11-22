package dev.specbuilder.generator.code;

import dev.specbuilder.generator.*;
import freemarker.cache.ClassTemplateLoader;
import freemarker.core.PlainTextOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Renders a project into a Maven project through the templates of one target. The target lives
 * entirely in {@code resources/codegen/<target>/}: its manifest says what to render.
 */
@Component
public class CodeGenerator implements Generator {

    public static final String ID = "code";
    public static final String EXECUTABLE = "application/x-sh";
    private static final String TARGET = "spring-boot-4";

    /** The files, plus which file holds each endpoint's logic (for jumping to it in the preview). */
    public record Output(GenerationResult result, Map<String, String> endpointFiles, String targetName) {
    }

    private final TargetManifest manifest;
    private final Configuration freemarker;

    public CodeGenerator() {
        this.manifest = loadManifest();
        this.freemarker = new Configuration(Configuration.VERSION_2_3_34);
        freemarker.setTemplateLoader(new ClassTemplateLoader(CodeGenerator.class, "/codegen/" + TARGET));
        freemarker.setObjectWrapper(new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_34).build());
        freemarker.setDefaultEncoding("UTF-8");
        freemarker.setOutputFormat(PlainTextOutputFormat.INSTANCE);
        freemarker.setNumberFormat("computer");
        freemarker.setBooleanFormat("c");
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarker.setLogTemplateExceptions(false);
        freemarker.setWrapUncheckedExceptions(true);
        freemarker.setFallbackOnNullLoopVariable(false);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public GenerationResult generate(ProjectSnapshot project) {
        return render(project).result();
    }

    /** The code model alone (names, classes, steps), e.g. for diagrams of the generated code. */
    public CodeModel model(ProjectSnapshot project) {
        return new CodeModelBuilder(project, Set.copyOf(manifest.reservedNames())).build();
    }

    public Output render(ProjectSnapshot project) {
        CodeModelBuilder builder = new CodeModelBuilder(project, Set.copyOf(manifest.reservedNames()));
        CodeModel model = builder.build();
        List<Problem> problems = new ArrayList<>(builder.problems());
        Map<String, Object> root = new HashMap<>();
        root.put("model", model);
        root.put("project", model.project());
        root.put("features", model.features());
        root.put("target", manifest.properties());

        List<GeneratedFile> files = new ArrayList<>();
        Map<String, String> endpointFiles = new TreeMap<>();
        for (TargetManifest.FileEntry entry : manifest.files()) {
            for (Object item : items(entry, model)) {
                Map<String, Object> data = new HashMap<>(root);
                data.put("item", item);
                try {
                    if (entry.when() != null && renderString("<#if " + entry.when() + ">1</#if>", data).isEmpty()) {
                        continue;
                    }
                    String path = renderString(entry.output(), data);
                    String content = entry.copy() ? resource(entry.template()) : renderTemplate(entry.template(), data);
                    files.add(new GeneratedFile(path, content, entry.executable() ? EXECUTABLE : mediaType(path)));
                    if (entry.primary()) {
                        endpointNames(item).forEach(name -> endpointFiles.put(name, path));
                    }
                } catch (TemplateException | IOException | RuntimeException e) {
                    problems.add(Problem.error("template", entry.template(), "rendering failed: " + e.getMessage().lines().findFirst().orElse("")));
                }
            }
        }
        files.sort(Comparator.comparing(GeneratedFile::path));
        return new Output(new GenerationResult(files, problems), endpointFiles, manifest.name());
    }

    private static List<?> items(TargetManifest.FileEntry entry, CodeModel model) {
        if (entry.items() != null) {
            return entry.items();
        }
        if (entry.each() == null) {
            return Collections.singletonList(null);
        }
        try {
            Method accessor = CodeModel.class.getMethod(entry.each());
            return (List<?>) accessor.invoke(model);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("manifest entry " + entry.template() + " uses unknown collection " + entry.each(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> endpointNames(Object item) {
        try {
            return item == null ? List.of() : (List<String>) item.getClass().getMethod("endpointNames").invoke(item);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    private String renderTemplate(String name, Map<String, Object> data) throws IOException, TemplateException {
        StringWriter out = new StringWriter();
        freemarker.getTemplate(name).process(data, out);
        return out.toString();
    }

    private String renderString(String text, Map<String, Object> data) throws IOException, TemplateException {
        StringWriter out = new StringWriter();
        new Template("inline", new StringReader(text), freemarker).process(data, out);
        return out.toString();
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = CodeGenerator.class.getResourceAsStream("/codegen/" + TARGET + "/" + name)) {
            if (in == null) throw new IOException("missing template file " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String mediaType(String path) {
        if (path.endsWith(".java")) return "text/x-java";
        if (path.endsWith(".xml")) return "application/xml";
        if (path.endsWith(".yml") || path.endsWith(".yaml")) return "application/yaml";
        if (path.endsWith(".md")) return "text/markdown";
        return "text/plain";
    }

    private static TargetManifest loadManifest() {
        try (InputStream in = CodeGenerator.class.getResourceAsStream("/codegen/" + TARGET + "/manifest.yml")) {
            // Flags left out of an entry (copy, executable, primary) mean false.
            return YAMLMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build().readValue(in, TargetManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
