package dev.specbuilder.importexport;

import dev.specbuilder.api.dto.ParameterDto;
import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.RuleType;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * A whole project as one portable JSON document. Payloads are referred to by name everywhere
 * (fields, bodies, rule configs), so the file reads well and imports into any instance.
 * In rule configs, a "...PayloadId" setting becomes "...Payload" holding the payload's name.
 * Error codes are referred to by their code. {@code fieldInventory} holds the reusable field definitions;
 * {@code capabilities} the capability tree, and {@code capabilityLevels} the level names if not the defaults.
 */
public record ProjectExport(String format, Integer version, ProjectData project, List<PayloadData> payloads,
                            List<EndpointData> endpoints, List<ErrorCodeData> errorCodes, List<FieldData> fieldInventory,
                            List<String> capabilityLevels, List<CapabilityData> capabilities) {

    public ProjectExport(String format, Integer version, ProjectData project, List<PayloadData> payloads, List<EndpointData> endpoints,
                         List<ErrorCodeData> errorCodes, List<FieldData> fieldInventory) {
        this(format, version, project, payloads, endpoints, errorCodes, fieldInventory, null, null);
    }

    public ProjectExport(String format, Integer version, ProjectData project, List<PayloadData> payloads, List<EndpointData> endpoints) {
        this(format, version, project, payloads, endpoints, null, null, null, null);
    }

    public ProjectExport(String format, Integer version, ProjectData project, List<PayloadData> payloads, List<EndpointData> endpoints,
                         List<ErrorCodeData> errorCodes) {
        this(format, version, project, payloads, endpoints, errorCodes, null, null, null);
    }

    /**
     * A functional capability, with the capabilities below it nested in {@code children} (up to eight levels)
     * and the APIs linked to it by endpoint name.
     */
    public record CapabilityData(String name, String code, String description, List<String> apis, List<CapabilityData> children) {
    }

    /** An entry of the error-code inventory. Fields, rules and checks refer to it by {@code code}. */
    public record ErrorCodeData(String code, String message, Integer httpStatus, ErrorCategory category, String description) {
    }

    public static final String FORMAT = "spec-builder/project";
    public static final int VERSION = 1;

    public record ProjectData(String name, String basePackage, String version, String description,
                              ImplementationStyle defaultImplementationStyle, Boolean allowCircularReferences) {
    }

    /** {@code role} may be left out (older exports): payloads other payloads use as a field type become OBJECTs. */
    public record PayloadData(String name, String description, String example, List<FieldData> fields, PayloadRole role) {

        public PayloadData(String name, String description, String example, List<FieldData> fields) {
            this(name, description, example, fields, null);
        }
    }

    public record FieldData(String name, FieldType type, FieldType itemType, String refPayload, Boolean required,
                            String format, String description, String example, Integer minLength, Integer maxLength,
                            Double minimum, Double maximum, String pattern, List<String> enumValues, List<String> errorCodes) {

        public FieldData(String name, FieldType type, FieldType itemType, String refPayload, Boolean required,
                         String format, String description, String example, Integer minLength, Integer maxLength,
                         Double minimum, Double maximum, String pattern, List<String> enumValues) {
            this(name, type, itemType, refPayload, required, format, description, example, minLength, maxLength, minimum, maximum,
                    pattern, enumValues, null);
        }
    }

    public record ResponseData(String statusCode, String description, String payload) {
    }

    /** In {@code config}, a check's "errorCodeId" becomes "errorCode" holding the code. */
    public record RuleData(RuleType type, String name, String condition, JsonNode config, List<String> errorCodes) {

        public RuleData(RuleType type, String name, String condition, JsonNode config) {
            this(type, name, condition, config, null);
        }
    }

    public record EndpointData(EndpointKind kind, String name, String summary, String description, List<String> tags,
                               ImplementationStyle implementationStyle, List<ParameterDto> parameters,
                               HttpMethod httpMethod, String path, String requestPayload, List<ResponseData> responses,
                               List<SecurityScheme> security, String channel, AsyncOperation operation,
                               MessagingProtocol protocol, String messagePayload, List<RuleData> rules) {
    }
}
