package dev.specbuilder.api;

import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.*;
import dev.specbuilder.expression.Functions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/** The allowed values of every enum, so the editor can build its drop-downs from one source. */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    public record RuleTypeInfo(RuleType type, String label) {
    }

    public record FunctionInfo(String name, String example, String description) {
    }

    @GetMapping
    public Map<String, Object> meta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fieldTypes", FieldType.values());
        meta.put("endpointKinds", EndpointKind.values());
        meta.put("httpMethods", HttpMethod.values());
        meta.put("parameterLocations", ParameterLocation.values());
        meta.put("asyncOperations", AsyncOperation.values());
        meta.put("protocols", MessagingProtocol.values());
        meta.put("securitySchemes", SecurityScheme.values());
        meta.put("implementationStyles", ImplementationStyle.values());
        meta.put("payloadRoles", PayloadRole.values());
        meta.put("ruleTypes", Arrays.stream(RuleType.values()).map(t -> new RuleTypeInfo(t, t.label())).toList());
        meta.put("validateCheckKinds", ValidateConfig.CheckKind.values());
        meta.put("enrichModes", EnrichConfig.Mode.values());
        meta.put("persistOperations", PersistConfig.Operation.values());
        meta.put("aggregations", SplitAggregateConfig.Aggregation.values());
        meta.put("errorCategories", ErrorHandlingConfig.ErrorCategory.values());
        meta.put("fallbackTypes", ErrorHandlingConfig.FallbackType.values());
        meta.put("expressionFunctions", Arrays.stream(Functions.values())
                .map(f -> new FunctionInfo(f.fnName(), f.example(), f.description())).toList());
        return meta;
    }
}
