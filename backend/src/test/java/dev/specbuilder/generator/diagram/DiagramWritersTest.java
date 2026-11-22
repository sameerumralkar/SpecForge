package dev.specbuilder.generator.diagram;

import dev.specbuilder.generator.diagram.DiagramModel.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagramWritersTest {

    private final Sequence sequence = new Sequence(
            List.of(new Participant("c1", "Client", ParticipantType.ACTOR), new Participant("s2", "OrdersServiceImpl", ParticipantType.PARTICIPANT),
                    new Participant("db3", "Database", ParticipantType.DATABASE), new Participant("q4", "orders (Kafka)", ParticipantType.QUEUE)),
            List.of(new Message("c1", "s2", "POST /orders", MessageType.CALL),
                    new Block(BlockType.ALT, List.of(new Section("total > 100", List.of(new Message("s2", "s2", "bigOrder", MessageType.CALL))),
                            new Section("otherwise", List.of()))),
                    new Block(BlockType.BREAK, List.of(new Section("not found", List.of(new Message("s2", "c1", "404", MessageType.REPLY))))),
                    new Message("s2", "q4", "List<Item>; #1", MessageType.ASYNC),
                    new Note("s2", "done")));

    @Test
    void writesMermaidSequencesWithEscapedText() {
        String m = MermaidWriter.sequence(sequence);
        assertThat(m).startsWith("sequenceDiagram\n  autonumber\n")
                .contains("  actor c1 as Client\n")
                .contains("  participant db3 as Database\n")
                .contains("  alt total #gt; 100\n    s2->>s2: bigOrder\n  else otherwise\n    Note over s2: otherwise\n  end\n")
                .contains("  break not found\n    s2-->>c1: 404\n  end\n")
                .contains("  s2-)q4: List#lt;Item#gt;#59; #35;1\n")
                .contains("  Note over s2: done\n");
    }

    @Test
    void writesPlantUmlSequencesWithParticipantShapes() {
        String p = PlantUmlWriter.sequence(sequence, "createOrder");
        assertThat(p).startsWith("@startuml\ntitle createOrder\nautonumber\n").endsWith("@enduml\n")
                .contains("database \"Database\" as db3\n")
                .contains("queue \"orders (Kafka)\" as q4\n")
                .contains("alt total > 100\n  s2 -> s2 : bigOrder\nelse otherwise\nend\n")
                .contains("s2 ->> q4 : List<Item>; #1\n");
        assertThat(new PlantUmlRenderer().syntaxError(p)).isNull();
    }

    @Test
    void writesClassDiagramsInBothNotations() {
        ClassDiagram d = new ClassDiagram(
                List.of(new DiagramModel.Package("dto", List.of(
                                new ClassBox("Order", null, false, List.of(new Member(Visibility.PRIVATE, "items", "List<Item>", false, null))),
                                new ClassBox("Item", null, false, List.of()))),
                        new DiagramModel.Package("service", List.of(
                                new ClassBox("OrdersService", null, true, List.of(new Member(Visibility.PUBLIC, "createOrder", "Order", true, "Order"))),
                                new ClassBox("OrdersServiceImpl", "Service", false, List.of(new Member(Visibility.PRIVATE, "validate", null, true, "")))))),
                List.of(new Relation("Order", "Item", RelationKind.COMPOSITION, "items", "0..*"),
                        new Relation("OrdersServiceImpl", "OrdersService", RelationKind.REALIZATION, null, null)));
        String m = MermaidWriter.classes(d);
        assertThat(m).contains("  namespace dto {\n    class Order {\n      -List~Item~ items\n    }\n")
                .contains("    class OrdersService {\n      <<interface>>\n      +createOrder(Order) Order\n")
                .contains("      <<Service>>\n      -validate()\n")
                .contains("  Order *-- \"0..*\" Item : items\n")
                .contains("  OrdersServiceImpl ..|> OrdersService\n");
        String p = PlantUmlWriter.classes(d, "classes");
        assertThat(p).contains("!pragma layout smetana\n").contains("interface OrdersService {\n    +createOrder(Order) : Order\n")
                .contains("class OrdersServiceImpl <<Service>> {\n").contains("Order *-- \"0..*\" Item : items\n");
        assertThat(new PlantUmlRenderer().syntaxError(p)).isNull();
    }

    @Test
    void writesOverviewsInBothNotations() {
        Overview o = new Overview(List.of(new Node("svc", "Order Service", NodeType.SERVICE), new Node("db", "Database", NodeType.DATABASE),
                new Node("q1", "orders (Kafka)", NodeType.QUEUE), new Node("clients", "REST clients", NodeType.CLIENTS)),
                List.of(new Edge("clients", "svc", "GET /orders/{id}"), new Edge("svc", "db", null), new Edge("q1", "svc", "receive")));
        assertThat(MermaidWriter.overview(o)).startsWith("flowchart LR\n").contains("  db[(\"Database\")]\n").contains("  q1>\"orders (Kafka)\"]\n")
                .contains("  clients -->|\"GET /orders/{id}\"| svc\n").contains("  svc --> db\n");
        String p = PlantUmlWriter.overview(o, "overview");
        assertThat(p).contains("database \"Database\" as db\n").contains("q1 --> svc : receive\n");
        assertThat(new PlantUmlRenderer().syntaxError(p)).isNull();
    }

    @Test
    void reportsPlantUmlSyntaxErrors() {
        assertThat(new PlantUmlRenderer().syntaxError("@startuml\nclass {{{\n@enduml\n")).startsWith("PlantUML could not parse");
    }
}
