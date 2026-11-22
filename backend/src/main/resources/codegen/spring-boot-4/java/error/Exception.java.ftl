package ${project.basePackage}.error;

/** ${item.doc} */
public class ${item.name} extends RuntimeException {
<#if item.name == "RuleViolationException">

    private final int status;

    public RuleViolationException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
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
