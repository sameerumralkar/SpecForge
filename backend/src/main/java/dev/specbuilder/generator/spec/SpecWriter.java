package dev.specbuilder.generator.spec;

import tools.jackson.databind.JsonNode;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/** Writes a document tree as YAML and JSON with fixed settings, so output is stable. */
public final class SpecWriter {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Two-space indent, "key": value, one array item per line and "\n" line ends on every OS. */
    private static final ObjectWriter JSON_WRITER = JSON.writer().with(prettyPrinter());

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .disable(YAMLWriteFeature.SPLIT_LINES)
            .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
            .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
            .build();

    private SpecWriter() {
    }

    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(Separators.createDefaultInstance()
                .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator(""));
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    public static String json(Object tree) {
        return JSON_WRITER.writeValueAsString(tree) + "\n";
    }

    public static String yaml(Object tree) {
        return YAML.writeValueAsString(tree);
    }

    public static JsonNode readJson(String json) {
        return JSON.readTree(json);
    }

    public static JsonNode readYaml(String yaml) {
        return YAML.readTree(yaml);
    }
}
