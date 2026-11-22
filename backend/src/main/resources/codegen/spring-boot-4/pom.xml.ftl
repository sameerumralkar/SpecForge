<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>${target.bootVersion}</version>
        <relativePath/>
    </parent>

    <groupId>${project.groupId}</groupId>
    <artifactId>${project.artifactId}</artifactId>
    <version>${project.version}</version>
    <name>${project.name?xml}</name>
<#if project.description??>
    <description>${project.description?xml}</description>
</#if>

    <properties>
        <java.version>${target.javaVersion}</java.version>
<#if features.camel>
        <camel.version>${target.camelVersion}</camel.version>
</#if>
<#if features.mapstruct>
        <mapstruct.version>${target.mapstructVersion}</mapstruct.version>
</#if>
<#if features.retry>
        <resilience4j.version>${target.resilience4jVersion}</resilience4j.version>
</#if>
    </properties>
<#if features.camel>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.apache.camel.springboot</groupId>
                <artifactId>camel-spring-boot-bom</artifactId>
                <version>${r"${camel.version}"}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</#if>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jackson</artifactId>
        </dependency>
<#if features.jpa>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
</#if>
<#if features.springKafka>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-kafka</artifactId>
        </dependency>
</#if>
<#if features.jms>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-artemis</artifactId>
        </dependency>
</#if>
<#if features.amqp>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>
</#if>
<#if features.camel>
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-direct-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-bean-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-bean-validator-starter</artifactId>
        </dependency>
</#if>
<#if features.camelKafka>
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-kafka-starter</artifactId>
        </dependency>
</#if>
<#if features.camelJms>
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-jms-starter</artifactId>
        </dependency>
</#if>
<#if features.camelAmqp>
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-spring-rabbitmq-starter</artifactId>
        </dependency>
</#if>
<#if features.mapstruct>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${r"${mapstruct.version}"}</version>
        </dependency>
</#if>
<#if features.retry>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-retry</artifactId>
            <version>${r"${resilience4j.version}"}</version>
        </dependency>
</#if>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
<#if features.mapstruct>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <parameters>true</parameters>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${r"${mapstruct.version}"}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
</#if>
        </plugins>
    </build>
</project>
