package dev.specbuilder.expression;

/** A problem that makes an expression invalid whatever payload it runs against. */
public class ExpressionException extends RuntimeException {

    private final int position;

    public ExpressionException(String message, int position) {
        super(message);
        this.position = position;
    }

    public int position() {
        return position;
    }

    /** "at character 8: expected ')'" (1-based, as people count). */
    public String describe() {
        return "at character " + (position + 1) + ": " + getMessage();
    }
}
