<#import "/lib/java.ftl" as j>
package ${project.basePackage}.service;

import ${project.basePackage}.dto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Operations tagged "${item.name}". */
public interface ${item.serviceClass} {
<#list item.implEndpoints as e>

    /** ${j.doc(e.comment)} */
    ${e.impl.chainReturnType} ${e.name}(${e.serviceSignature});
</#list>
}
