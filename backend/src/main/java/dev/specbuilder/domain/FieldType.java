package dev.specbuilder.domain;

public enum FieldType {
    STRING, INTEGER, LONG, NUMBER, DECIMAL, BOOLEAN, DATE, DATE_TIME, UUID, OBJECT, ARRAY;

    public boolean isScalar() {
        return this != OBJECT && this != ARRAY;
    }

    public boolean isNumeric() {
        return this == INTEGER || this == LONG || this == NUMBER || this == DECIMAL;
    }
}
