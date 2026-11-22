<#import "/lib/java.ftl" as j>
package ${project.basePackage}.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

<#if item.description??>
/** ${j.doc(item.description)} */
</#if>
public class ${item.className} {
<#list item.fields as f>

<#if f.description??>
    /** ${j.doc(f.description)} */
</#if>
<#list f.annotations as a>
    ${a}
</#list>
    private ${f.javaType} ${f.name};
</#list>

    public ${item.className}() {
    }
<#list item.fields as f>

    public ${f.plainType} ${f.getter()}() {
        return ${f.name};
    }

    public void ${f.setter()}(${f.plainType} ${f.name}) {
        this.${f.name} = ${f.name};
    }
</#list>
}
