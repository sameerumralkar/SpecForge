<#import "/lib/java.ftl" as j>
package ${project.basePackage}.error;

/**
 * The error codes of ${j.doc(project.name)}, from the SpecForge error-code inventory. Error responses carry the
 * code in the problem's "code" property.
 */
public enum ErrorCatalog {
<#list model.errorCodes as c>

    /** ${c.category}<#if c.description??>: ${j.doc(c.description)}</#if> */
    ${c.constant}("${c.code}", ${c.status}, ${c.message})<#if c?has_next>,<#else>;</#if>
</#list>

    private final String code;
    private final int status;
    private final String message;

    ErrorCatalog(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String message() {
        return message;
    }

    /** The exception that answers with this code, its status and its message. */
    public RuleViolationException violation() {
        return new RuleViolationException(status, code, message);
    }

    /** The same, with a more specific message. */
    public RuleViolationException violation(String detail) {
        return new RuleViolationException(status, code, detail);
    }
}
