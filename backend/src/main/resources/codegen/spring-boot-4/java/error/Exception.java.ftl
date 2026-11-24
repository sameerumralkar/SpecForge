package ${project.basePackage}.error;

/** ${item.doc} */
public class ${item.name} extends RuntimeException {
<#if item.name == "RuleViolationException">

    private final int status;
    private final String code;

    public RuleViolationException(int status, String message) {
        this(status, null, message);
    }

    /** {@code code} is an error code from ErrorCatalog, or null. */
    public RuleViolationException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    /** The error code callers see in the problem response's "code" property, or null. */
    public String getCode() {
        return code;
    }
<#elseif item.name == "NotFoundException">

    public NotFoundException(String message) {
        super(message);
    }
<#else>

    public ${item.name}(String message, Throwable cause) {
        super(message, cause);
    }
</#if>
}
