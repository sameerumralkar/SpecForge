package dev.specbuilder.generator.diagram;

import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Renders PlantUML on the server, and checks sources for syntax errors without rendering them. */
@Component
public class PlantUmlRenderer {

    public enum Format { SVG, PNG }

    public byte[] render(String source, Format format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new SourceStringReader(source).outputImage(out, new FileFormatOption(format == Format.SVG ? FileFormat.SVG : FileFormat.PNG));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Null when the source parses, otherwise a short description of the error. */
    public String syntaxError(String source) {
        SourceStringReader reader = new SourceStringReader(source);
        if (reader.getBlocks().isEmpty()) {
            return "no @startuml block";
        }
        BlockUml block = reader.getBlocks().getFirst();
        var diagram = block.getDiagram();
        if (diagram.getClass().getSimpleName().startsWith("PSystemError")) {
            return "PlantUML could not parse the diagram: " + diagram.getDescription().getDescription();
        }
        return null;
    }
}
