package dev.specbuilder.validation;

import dev.specbuilder.domain.FieldType;
import dev.specbuilder.domain.Payload;
import dev.specbuilder.domain.PayloadField;
import dev.specbuilder.domain.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldPathResolverTest {

    private final Project project = new Project();
    private final Payload address = payload("Address", field("city", FieldType.STRING, null, null));
    private final Payload item = payload("Item", field("quantity", FieldType.INTEGER, null, null));
    private final Payload order = payload("Order",
            field("id", FieldType.UUID, null, null),
            field("shipTo", FieldType.OBJECT, null, address),
            field("items", FieldType.ARRAY, FieldType.OBJECT, item),
            field("tags", FieldType.ARRAY, FieldType.STRING, null));

    @Test
    void resolvesTopLevelNestedAndArrayItemPaths() {
        assertThat(FieldPathResolver.resolve(order, "id").valueType()).isEqualTo(FieldType.UUID);
        assertThat(FieldPathResolver.resolve(order, "shipTo.city").valueType()).isEqualTo(FieldType.STRING);
        assertThat(FieldPathResolver.resolve(order, "items[].quantity").valueType()).isEqualTo(FieldType.INTEGER);
        assertThat(FieldPathResolver.resolve(order, "items").valueType()).isEqualTo(FieldType.ARRAY);
        assertThat(FieldPathResolver.resolve(order, "tags[]").valueType()).isEqualTo(FieldType.STRING);
    }

    @Test
    void explainsWhatIsWrong() {
        assertThat(FieldPathResolver.resolve(order, "total").error()).isEqualTo("'total' is not a field of Order");
        assertThat(FieldPathResolver.resolve(order, "shipTo.zip").error()).isEqualTo("'zip' is not a field of Address");
        assertThat(FieldPathResolver.resolve(order, "items.quantity").error()).contains("write 'items[]'");
        assertThat(FieldPathResolver.resolve(order, "id[]").error()).contains("is not an array");
        assertThat(FieldPathResolver.resolve(order, "id.value").error()).contains("is not an object");
        assertThat(FieldPathResolver.resolve(order, "tags[].x").error()).contains("is not an object");
        assertThat(FieldPathResolver.resolve(null, "id").error()).contains("no payload");
    }

    private Payload payload(String name, PayloadField... fields) {
        Payload p = new Payload(project);
        p.setName(name);
        for (PayloadField f : fields) {
            p.getFields().add(f);
        }
        return p;
    }

    private static PayloadField field(String name, FieldType type, FieldType itemType, Payload ref) {
        PayloadField f = new PayloadField();
        f.setName(name);
        f.setType(type);
        f.setItemType(itemType);
        f.setRefPayload(ref);
        return f;
    }
}
