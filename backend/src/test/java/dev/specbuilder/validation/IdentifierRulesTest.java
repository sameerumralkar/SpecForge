package dev.specbuilder.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"Order", "OrderItem", "V2Order", "A"})
    void acceptsUpperCamelTypeNames(String name) {
        assertThat(IdentifierRules.checkTypeName(name)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"order", "Order-Item", "Order_Item", "1Order", "Ordér", "Order$"})
    void rejectsMalformedTypeNames(String name) {
        assertThat(IdentifierRules.checkTypeName(name)).contains("UpperCamelCase");
    }

    @Test
    void rejectsTypeNamesThatShadowJavaClasses() {
        assertThat(IdentifierRules.checkTypeName("String")).contains("clashes");
        assertThat(IdentifierRules.checkTypeName("List")).contains("clashes");
    }

    @Test
    void memberNamesMustBeLowerCamelAndNotKeywords() {
        assertThat(IdentifierRules.checkMemberName("createOrder")).isNull();
        assertThat(IdentifierRules.checkMemberName("CreateOrder")).contains("lowerCamelCase");
        assertThat(IdentifierRules.checkMemberName("new")).contains("reserved");
        assertThat(IdentifierRules.checkMemberName(null)).isEqualTo("is required");
    }

    @Test
    void fieldIdentifiersAllowUnderscoreButNotKeywords() {
        assertThat(IdentifierRules.checkIdentifier("first_name")).isNull();
        assertThat(IdentifierRules.checkIdentifier("_internal")).isNull();
        assertThat(IdentifierRules.checkIdentifier("class")).contains("reserved");
        assertThat(IdentifierRules.checkIdentifier("record")).contains("reserved");
        assertThat(IdentifierRules.checkIdentifier("2fa")).contains("must start");
    }

    @Test
    void packageNamesAreDottedLowerCaseSegments() {
        assertThat(IdentifierRules.checkPackageName("com.example.orders")).isNull();
        assertThat(IdentifierRules.checkPackageName("orders")).isNull();
        assertThat(IdentifierRules.checkPackageName("com.Example")).contains("lower-case");
        assertThat(IdentifierRules.checkPackageName("com..example")).contains("lower-case");
        assertThat(IdentifierRules.checkPackageName("com.example.")).contains("lower-case");
        assertThat(IdentifierRules.checkPackageName("com.int.orders")).contains("reserved");
    }
}
