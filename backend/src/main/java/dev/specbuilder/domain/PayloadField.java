package dev.specbuilder.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Embeddable
public class PayloadField {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldType type;

    /** Element type when {@link #type} is ARRAY. */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 20)
    private FieldType itemType;

    /** The referenced payload for OBJECT fields and for ARRAY fields whose items are OBJECT. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ref_payload_id")
    private Payload refPayload;

    @Column(nullable = false)
    private boolean required;

    @Column(length = 50)
    private String format;

    @Column(length = 2000)
    private String description;

    @Column(length = 1000)
    private String example;

    @Column(name = "min_length")
    private Integer minLength;

    @Column(name = "max_length")
    private Integer maxLength;

    private Double minimum;

    private Double maximum;

    @Column(length = 500)
    private String pattern;

    @Convert(converter = StringListConverter.class)
    @Column(name = "enum_values", length = 4000)
    private List<String> enumValues = new ArrayList<>();

    /** Ids of the error codes this field can fail with, in order; the first is the one checks use. */
    @Convert(converter = StringListConverter.class)
    @Column(name = "error_codes", length = 2000)
    private List<String> errorCodes = new ArrayList<>();

    public PayloadField() {
    }

    public PayloadField copy() {
        PayloadField c = new PayloadField();
        c.name = name;
        c.type = type;
        c.itemType = itemType;
        c.refPayload = refPayload;
        c.required = required;
        c.format = format;
        c.description = description;
        c.example = example;
        c.minLength = minLength;
        c.maxLength = maxLength;
        c.minimum = minimum;
        c.maximum = maximum;
        c.pattern = pattern;
        c.enumValues = new ArrayList<>(enumValues == null ? List.of() : enumValues);
        c.errorCodes = new ArrayList<>(errorCodes == null ? List.of() : errorCodes);
        return c;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FieldType getType() { return type; }
    public void setType(FieldType type) { this.type = type; }
    public FieldType getItemType() { return itemType; }
    public void setItemType(FieldType itemType) { this.itemType = itemType; }
    public Payload getRefPayload() { return refPayload; }
    public void setRefPayload(Payload refPayload) { this.refPayload = refPayload; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
    public Integer getMinLength() { return minLength; }
    public void setMinLength(Integer minLength) { this.minLength = minLength; }
    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
    public Double getMinimum() { return minimum; }
    public void setMinimum(Double minimum) { this.minimum = minimum; }
    public Double getMaximum() { return maximum; }
    public void setMaximum(Double maximum) { this.maximum = maximum; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public List<String> getEnumValues() { return enumValues == null ? List.of() : enumValues; }
    public void setEnumValues(List<String> enumValues) { this.enumValues = new ArrayList<>(enumValues == null ? List.of() : enumValues); }

    public List<UUID> getErrorCodeIds() { return ErrorCodeIds.read(errorCodes); }
    public void setErrorCodeIds(List<UUID> ids) { this.errorCodes = ErrorCodeIds.write(ids); }

    /** The type a value of this field has once you step into array items with {@code name[]}. */
    public FieldType elementType() {
        return type == FieldType.ARRAY ? itemType : type;
    }
}
