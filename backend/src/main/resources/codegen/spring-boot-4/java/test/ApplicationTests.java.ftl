package ${project.basePackage};

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Starts the whole application<#if features.kafka || features.jms || features.amqp>, which needs the message brokers</#if>; enable once they are available")
class ${project.appClass}Tests {

    @Test
    void contextLoads() {
    }
}
