package dev.specbuilder.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/** Stores a list of strings as a JSON array in one text column. */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LIST = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        return values == null || values.isEmpty() ? null : JSON.writeValueAsString(values);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        return column == null || column.isBlank() ? new ArrayList<>() : new ArrayList<>(JSON.readValue(column, LIST));
    }
}
