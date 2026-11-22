package ${project.basePackage}.messaging;

import ${project.basePackage}.error.MessagingFailedException;
<#if features.springAmqp>
import org.springframework.amqp.rabbit.core.RabbitTemplate;
</#if>
<#if features.springJms>
import org.springframework.jms.core.JmsTemplate;
</#if>
<#if features.springKafka>
import org.springframework.kafka.core.KafkaTemplate;
</#if>
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Publishes messages as JSON. Failures become {@link MessagingFailedException}. */
@Component
public class MessagingGateway {

    private final JsonMapper json;
<#if features.springKafka>
    private final KafkaTemplate<String, String> kafka;
</#if>
<#if features.springJms>
    private final JmsTemplate jms;
</#if>
<#if features.springAmqp>
    private final RabbitTemplate rabbit;
</#if>

    public MessagingGateway(JsonMapper json<#if features.springKafka>, KafkaTemplate<String, String> kafka</#if><#if features.springJms>, JmsTemplate jms</#if><#if features.springAmqp>, RabbitTemplate rabbit</#if>) {
        this.json = json;
<#if features.springKafka>
        this.kafka = kafka;
</#if>
<#if features.springJms>
        this.jms = jms;
</#if>
<#if features.springAmqp>
        this.rabbit = rabbit;
</#if>
    }
<#if features.springKafka>

    public void sendKafka(String topic, String key, Object payload) {
        try {
            kafka.send(topic, key, json.writeValueAsString(payload));
        } catch (RuntimeException e) {
            throw new MessagingFailedException("Could not publish to Kafka topic " + topic, e);
        }
    }
</#if>
<#if features.springJms>

    public void sendJms(String destination, Object payload) {
        try {
            jms.convertAndSend(destination, json.writeValueAsString(payload));
        } catch (RuntimeException e) {
            throw new MessagingFailedException("Could not send to JMS destination " + destination, e);
        }
    }
</#if>
<#if features.springAmqp>

    public void sendAmqp(String exchange, Object payload) {
        try {
            rabbit.convertAndSend(exchange, "", json.writeValueAsString(payload));
        } catch (RuntimeException e) {
            throw new MessagingFailedException("Could not publish to AMQP exchange " + exchange, e);
        }
    }
</#if>
}
