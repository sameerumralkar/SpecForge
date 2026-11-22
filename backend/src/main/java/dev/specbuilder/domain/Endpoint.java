package dev.specbuilder.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A REST operation (OpenAPI) or an async operation on a channel (AsyncAPI).
 * Kind-specific columns are null for the other kind.
 */
@Entity
@Table(name = "endpoint")
public class Endpoint {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EndpointKind kind;

    /** The operation id: a lowerCamelCase Java identifier, unique in the project. */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String summary;

    @Column(length = 4000)
    private String description;

    /** Null means "use the project default". */
    @Enumerated(EnumType.STRING)
    @Column(name = "implementation_style", length = 30)
    private ImplementationStyle implementationStyle;

    @ElementCollection
    @CollectionTable(name = "endpoint_tag", joinColumns = @JoinColumn(name = "endpoint_id"))
    @OrderColumn(name = "ordinal")
    @Column(name = "tag", nullable = false, length = 100)
    private List<String> tags = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "endpoint_parameter", joinColumns = @JoinColumn(name = "endpoint_id"))
    @OrderColumn(name = "ordinal")
    private List<ParameterDefinition> parameters = new ArrayList<>();

    // ---- REST ----

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", length = 10)
    private HttpMethod httpMethod;

    @Column(length = 500)
    private String path;

    /** The path with every {param} name blanked, so /orders/{id} and /orders/{orderId} count as the same route. */
    @Column(name = "path_key", length = 500)
    private String pathKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_payload_id")
    private Payload requestPayload;

    @ElementCollection
    @CollectionTable(name = "endpoint_response", joinColumns = @JoinColumn(name = "endpoint_id"))
    @OrderColumn(name = "ordinal")
    private List<ResponseDefinition> responses = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "endpoint_security", joinColumns = @JoinColumn(name = "endpoint_id"))
    @OrderColumn(name = "ordinal")
    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 20)
    private List<SecurityScheme> security = new ArrayList<>();

    // ---- ASYNC ----

    @Column(length = 255)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "async_operation", length = 10)
    private AsyncOperation asyncOperation;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private MessagingProtocol protocol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_payload_id")
    private Payload messagePayload;

    @OneToMany(mappedBy = "endpoint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal")
    private List<ProcessingRule> rules = new ArrayList<>();

    protected Endpoint() {
    }

    public Endpoint(Project project) {
        this.project = project;
    }

    public ImplementationStyle effectiveImplementationStyle() {
        return implementationStyle != null ? implementationStyle : project.getDefaultImplementationStyle();
    }

    /** The payload the processing rules start from: the request body for REST, the message for async. */
    public Payload inputPayload() {
        return kind == EndpointKind.REST ? requestPayload : messagePayload;
    }

    /** Puts rules back in a gap-free 0..n-1 order. */
    public void renumberRules() {
        for (int i = 0; i < rules.size(); i++) {
            rules.get(i).setOrdinal(i);
        }
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public EndpointKind getKind() { return kind; }
    public void setKind(EndpointKind kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ImplementationStyle getImplementationStyle() { return implementationStyle; }
    public void setImplementationStyle(ImplementationStyle style) { this.implementationStyle = style; }
    public List<String> getTags() { return tags; }
    public List<ParameterDefinition> getParameters() { return parameters; }
    public HttpMethod getHttpMethod() { return httpMethod; }
    public void setHttpMethod(HttpMethod httpMethod) { this.httpMethod = httpMethod; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getPathKey() { return pathKey; }
    public void setPathKey(String pathKey) { this.pathKey = pathKey; }
    public Payload getRequestPayload() { return requestPayload; }
    public void setRequestPayload(Payload requestPayload) { this.requestPayload = requestPayload; }
    public List<ResponseDefinition> getResponses() { return responses; }
    public List<SecurityScheme> getSecurity() { return security; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public AsyncOperation getAsyncOperation() { return asyncOperation; }
    public void setAsyncOperation(AsyncOperation asyncOperation) { this.asyncOperation = asyncOperation; }
    public MessagingProtocol getProtocol() { return protocol; }
    public void setProtocol(MessagingProtocol protocol) { this.protocol = protocol; }
    public Payload getMessagePayload() { return messagePayload; }
    public void setMessagePayload(Payload messagePayload) { this.messagePayload = messagePayload; }
    public List<ProcessingRule> getRules() { return rules; }

    public static <T> void replace(List<T> target, List<T> values) {
        target.clear();
        target.addAll(values);
    }
}
