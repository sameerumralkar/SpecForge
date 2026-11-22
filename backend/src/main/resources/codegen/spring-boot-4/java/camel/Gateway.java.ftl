<#import "/lib/java.ftl" as j>
package ${project.basePackage}.route;

import ${project.basePackage}.dto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

/** Starts the Camel routes of the "${item.name}" send operations. */
@Component
public class ${item.gatewayClass} {

    private final ProducerTemplate camel;

    public ${item.gatewayClass}(ProducerTemplate camel) {
        this.camel = camel;
    }
<#list item.gatewayEndpoints as e>

    /** ${j.doc(e.comment)} Runs the route direct:${e.name}. */
    public void ${e.name}(${e.serviceSignature}) {
        Map<String, Object> headers = new HashMap<>();
<#list e.params as p>
        headers.put("${p.name}", ${p.javaName});
</#list>
        camel.sendBodyAndHeaders("direct:${e.name}", ${e.messageVar}, headers);
    }
</#list>
}
