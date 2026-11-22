package ${project.basePackage}.client;

import ${project.basePackage}.dto.*;
import ${project.basePackage}.error.DownstreamTimeoutException;
import ${project.basePackage}.error.DownstreamUnavailableException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Calls ${item.serviceName}. Its base URL is the property ${item.propertyPrefix}.base-url. */
@Component
public class ${item.className} {

    private final RestClient restClient;
    private final Environment environment;

    public ${item.className}(Environment environment,
                             @Value("${r"${"}${item.propertyPrefix}.base-url:http://localhost:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(${item.timeoutMs?c}));
        requestFactory.setReadTimeout(Duration.ofMillis(${item.timeoutMs?c}));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.environment = environment;
    }
<#list item.methods as m>

    public ${m.responseType} ${m.name}(<#list m.uriParams as p>Object ${p}<#sep>, </#list><#if m.hasBody><#if m.uriParams?has_content>, </#if>Object body</#if>) {
        try {
            return restClient.method(HttpMethod.${m.httpMethod})
                    .uri(environment.resolvePlaceholders(${m.uri})<#list m.uriParams as p>, ${p}</#list>)
<#if m.hasBody>
                    .body(body)
</#if>
                    .retrieve()
                    .body(${m.responseType}.class);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new DownstreamTimeoutException("${item.serviceName} did not answer in time", e);
            }
            throw new DownstreamUnavailableException("${item.serviceName} could not be reached", e);
        } catch (HttpServerErrorException e) {
            throw new DownstreamUnavailableException("${item.serviceName} failed: " + e.getStatusCode(), e);
        }
    }
</#list>
}
