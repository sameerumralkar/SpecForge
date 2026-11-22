package dev.specbuilder.generator;

import java.util.ArrayList;
import java.util.List;

public record GenerationResult(List<GeneratedFile> files, List<Problem> problems) {

    public GenerationResult {
        files = List.copyOf(files);
        problems = List.copyOf(problems);
    }

    public GenerationResult withProblems(List<Problem> more) {
        List<Problem> all = new ArrayList<>(problems);
        all.addAll(more);
        return new GenerationResult(files, all);
    }

    public boolean hasErrors() {
        return problems.stream().anyMatch(p -> p.severity() == Problem.Severity.ERROR);
    }

    public GeneratedFile file(String path) {
        return files.stream().filter(f -> f.path().equals(path)).findFirst().orElse(null);
    }
}
