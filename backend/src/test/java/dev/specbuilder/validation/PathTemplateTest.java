package dev.specbuilder.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathTemplateTest {

    @Test
    void parsesParametersAndBuildsANameIndependentKey() {
        PathTemplate.Parsed parsed = PathTemplate.parse("/orders/{orderId}/items/{itemId}");
        assertThat(parsed.valid()).isTrue();
        assertThat(parsed.parameterNames()).containsExactly("orderId", "itemId");
        assertThat(parsed.key()).isEqualTo("/orders/{}/items/{}");
        assertThat(PathTemplate.parse("/orders/{id}/items/{x}").key()).isEqualTo(parsed.key());
    }

    @Test
    void rootPathIsValid() {
        assertThat(PathTemplate.parse("/").valid()).isTrue();
        assertThat(PathTemplate.parse("/").key()).isEqualTo("/");
    }

    @Test
    void reportsEachProblem() {
        assertThat(PathTemplate.parse("orders").errors()).contains("must start with '/'");
        assertThat(PathTemplate.parse("/orders/").errors()).contains("must not end with '/'");
        assertThat(PathTemplate.parse("/orders//items").errors()).contains("must not contain '//'");
        assertThat(PathTemplate.parse("/orders/{id}/x/{id}").errors()).anyMatch(e -> e.contains("more than once"));
        assertThat(PathTemplate.parse("/orders/{id}.json").errors()).anyMatch(e -> e.contains("not valid"));
        assertThat(PathTemplate.parse("/orders?x=1").errors()).anyMatch(e -> e.contains("not valid"));
        assertThat(PathTemplate.parse(" ").errors()).containsExactly("is required");
    }
}
