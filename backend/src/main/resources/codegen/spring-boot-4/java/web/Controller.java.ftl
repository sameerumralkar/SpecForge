<#import "/lib/java.ftl" as j>
package ${project.basePackage}.web;

import ${project.basePackage}.dto.*;
<#if item.hasService()>
import ${project.basePackage}.service.${item.serviceClass};
</#if>
<#if item.usesCamel()>
import ${project.basePackage}.support.CamelResponses;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
</#if>
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints tagged "${item.name}". */
@RestController
public class ${item.controllerClass} {
<#if item.hasService()>

    private final ${item.serviceClass} service;
</#if>
<#if item.usesCamel()>
    private final ProducerTemplate camel;
</#if>

    public ${item.controllerClass}(<#if item.hasService()>${item.serviceClass} service</#if><#if item.hasService() && item.usesCamel()>, </#if><#if item.usesCamel()>ProducerTemplate camel</#if>) {
<#if item.hasService()>
        this.service = service;
</#if>
<#if item.usesCamel()>
        this.camel = camel;
</#if>
    }
<#list item.restEndpoints as e>

    /** ${j.doc(e.comment)}<#if e.isRoute()> Runs the Camel route direct:${e.name}.</#if> */
    ${e.mappingAnnotation}
    public ResponseEntity<${e.returnType}> ${e.name}(${e.controllerArgs}) {
<#if e.isRoute()>
        Exchange result = camel.request("direct:${e.name}", exchange -> {
<#if e.bodyVar??>
            exchange.getMessage().setBody(${e.bodyVar});
</#if>
<#list e.params as p>
            exchange.getMessage().setHeader("${p.name}", ${p.javaName});
</#list>
        });
        return CamelResponses.respond(result, ${e.status}, ${e.returnType}.class);
<#elseif e.hasReturn()>
        return ResponseEntity.status(${e.status}).body(service.${e.name}(${e.serviceCallArgs}));
<#else>
        service.${e.name}(${e.serviceCallArgs});
        return ResponseEntity.status(${e.status}).build();
</#if>
    }
</#list>
}
