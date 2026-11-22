<#import "/lib/java.ftl" as j>
<#import "/lib/route-steps.ftl" as steps>
package ${project.basePackage}.route;

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
import ${project.basePackage}.support.Expressions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** The "${item.endpointName}" endpoint as a Camel route: each rule is a step. */
@Component
public class ${item.className} extends RouteBuilder {

<#list item.dependencies as d>
    private final ${d.type} ${d.name};
</#list>

    public ${item.className}(<#list item.dependencies as d>${d.type} ${d.name}<#sep>, </#list>) {
<#list item.dependencies as d>
        this.${d.name} = ${d.name};
</#list>
    }

    @Override
    public void configure() {
<#if item.deadLetter??>
<#assign dl = item.deadLetter>
        // ${j.doc(dl.summary)}
        errorHandler(deadLetterChannel("${dl.uri}")
            .useOriginalMessage()<#if dl.maxRedeliveries gt 0>
            .maximumRedeliveries(${dl.maxRedeliveries})
            .redeliveryDelay(${dl.delayMs?c}L)
            .backOffMultiplier(${dl.multiplier?c})
            .useExponentialBackOff()</#if>);

</#if>
<#list item.onExceptions as o>
        // ${j.doc(o.summary)}
        onException(${o.classes})<#if o.retry>
            .maximumRedeliveries(${o.maxRedeliveries})
            .redeliveryDelay(${o.delayMs?c}L)
            .backOffMultiplier(${o.multiplier?c})
            .useExponentialBackOff()</#if><#if (o.fallback!"") == "ERROR_RESPONSE">
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(${o.status}))
            .setBody(constant("${j.doc(o.message)?j_string}"))<#elseif (o.fallback!"") == "DEFAULT_RESPONSE">
            .handled(true)
            .process(exchange -> exchange.getMessage().setBody(<#if o.defaultType??>new ${o.defaultType}()<#else>null</#if>))</#if>;

</#list>
        from("${item.fromUri}")
            .routeId("${item.routeId}")
<#if item.unmarshal>
            .process(exchange -> exchange.getMessage().setBody(
                    json.readValue(exchange.getMessage().getBody(String.class), ${item.inputType}.class)))
</#if>
<#list item.steps as s>
<@steps.step s/>
</#list>
<#if item.toUri??>
            .process(exchange -> exchange.getMessage().setBody(json.writeValueAsString(exchange.getMessage().getBody())))
            .to("${item.toUri}")
</#if>
            ;
<@steps.subRoutes item/>
    }
}
