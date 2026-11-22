<#import "/lib/java.ftl" as j>
<#import "/lib/impl-steps.ftl" as steps>
package ${project.basePackage}.service;

import ${project.basePackage}.dto.*;
import ${project.basePackage}.error.*;
<#if features.jpa>
import ${project.basePackage}.persistence.*;
</#if>
<#if features.clients>
import ${project.basePackage}.client.*;
</#if>
<#if features.mapstruct>
import ${project.basePackage}.mapper.DtoMapper;
</#if>
<#if features.messaging>
import ${project.basePackage}.messaging.MessagingGateway;
</#if>
import ${project.basePackage}.support.Expressions;
<#if features.retry>
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
</#if>
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Operations tagged "${item.name}". Each endpoint is a public method that runs its rules in
 * order; each rule is a private method below it.
 */
@Service
public class ${item.serviceImplClass} implements ${item.serviceClass} {

    private static final Logger log = LoggerFactory.getLogger(${item.serviceImplClass}.class);

<#list item.implDependencies as d>
    private final ${d.type} ${d.name};
</#list>
<#list item.handlers?filter(h -> h.retry) as h>
    private final Retry ${h.methodName}Retry = Retry.of("${h.methodName}", RetryConfig.custom()
            .maxAttempts(${h.maxAttempts})
            .intervalFunction(IntervalFunction.ofExponentialBackoff(${h.backoffMs?c}L, ${h.multiplier?c}))
            .retryExceptions(${h.retryClasses?join(", ")})
            .build());
</#list>

    public ${item.serviceImplClass}(<#list item.implDependencies as d>${d.type} ${d.name}<#sep>, </#list>) {
<#list item.implDependencies as d>
        this.${d.name} = ${d.name};
</#list>
    }
<#list item.implEndpoints as e>

    /** ${j.doc(e.comment)} */
    @Override
    public ${e.impl.chainReturnType} ${e.name}(${e.serviceSignature}) {
<#if e.impl.chainMethod??>
        <#if e.impl.chainReturnType != "void">return </#if>${e.impl.wrappedChainCall};
    }

    private ${e.impl.chainReturnType} ${e.impl.chainMethod}(${e.impl.chainSignature}) {
</#if>
<#list e.impl.lines as l>
        ${j.indent(l, 8)}
</#list>
    }
<#list e.impl.steps as s>
<@steps.step s/>
</#list>
</#list>
<#list item.handlers as h>

    /** ${j.doc(h.name)}: ${j.doc(h.summary)} */
    private <T> T ${h.methodName}(Supplier<T> action, Supplier<T> fallback) {
<#if h.hasCatch>
        try {
            return <#if h.retry>${h.methodName}Retry.executeSupplier(action)<#else>action.get()</#if>;
        } catch (${h.catchClasses} e) {
            log.warn("${h.name} failed: {}", e.getMessage());
            return fallback.get();
        }
<#else>
        return <#if h.retry>${h.methodName}Retry.executeSupplier(action)<#else>action.get()</#if>;
</#if>
    }
</#list>
}
