<#import "/lib/java.ftl" as j>
<#assign kafka = item.listenerEndpoints?filter(e -> e.protocol == "KAFKA")?has_content>
<#assign jms = item.listenerEndpoints?filter(e -> e.protocol == "JMS")?has_content>
<#assign amqp = item.listenerEndpoints?filter(e -> e.protocol == "AMQP")?has_content>
package ${project.basePackage}.messaging;

import ${project.basePackage}.dto.*;
import ${project.basePackage}.service.${item.serviceClass};
import ${project.basePackage}.support.Expressions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
<#if amqp>
import org.springframework.amqp.rabbit.annotation.RabbitListener;
</#if>
<#if jms>
import org.springframework.jms.annotation.JmsListener;
</#if>
<#if kafka>
import org.springframework.kafka.annotation.KafkaListener;
</#if>
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Receives messages for the "${item.name}" operations and hands them to the service. */
@Component
public class ${item.listenerClass} {

    private final ${item.serviceClass} service;
    private final JsonMapper json;

    public ${item.listenerClass}(${item.serviceClass} service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }
<#list item.listenerEndpoints as e>

    /** ${j.doc(e.comment)} */
    ${e.listenerAnnotation?replace("@Queue(", "@org.springframework.amqp.rabbit.annotation.Queue(")}
    public void ${e.name}(String message<#list e.params as p>, ${p.listenerAnnotation?replace("@Header(", "@org.springframework.messaging.handler.annotation.Header(")}</#list>) {
        service.${e.name}(json.readValue(message, ${e.messageType}.class)<#list e.params as p>, ${p.listenerConversion}</#list>);
    }
</#list>
}
