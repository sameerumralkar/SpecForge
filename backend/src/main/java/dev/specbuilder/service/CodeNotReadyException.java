package dev.specbuilder.service;

import dev.specbuilder.generator.Problem;

import java.util.List;

public class CodeNotReadyException extends RuntimeException {

    private final List<Problem> problems;

    public CodeNotReadyException(List<Problem> problems) {
        super("The project has " + problems.size() + " problem" + (problems.size() == 1 ? "" : "s")
                + " to fix before code can be generated");
        this.problems = List.copyOf(problems);
    }

    public List<Problem> problems() {
        return problems;
    }
}
