spring:
  application:
    name: ${project.artifactId}
<#if features.jpa>
  datasource:
    # In-memory database for local runs; point this at your real database.
    url: jdbc:h2:mem:${project.artifactId};DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: update
</#if>
<#if features.kafka>
  kafka:
    bootstrap-servers: localhost:9092
</#if>
<#if features.jms>
  artemis:
    broker-url: tcp://localhost:61616
</#if>
<#if features.amqp>
  rabbitmq:
    host: localhost
    port: 5672
</#if>
<#if features.clients>

# Base URLs of the services the enrich rules call.
clients:
<#list model.clients as c>
  ${c.serviceName}:
    base-url: http://localhost:8081
</#list>
</#if>
<#if features.camel>

camel:
  main:
    name: ${project.artifactId}
</#if>
