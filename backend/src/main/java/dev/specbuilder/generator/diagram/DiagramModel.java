package dev.specbuilder.generator.diagram;

import java.util.List;

/** Notation-neutral diagrams; {@link MermaidWriter} and {@link PlantUmlWriter} turn them into text. */
public final class DiagramModel {

    private DiagramModel() {
    }

    // ---------------------------------------------------------------- class diagrams

    public record ClassDiagram(List<Package> packages, List<Relation> relations) {
    }

    public record Package(String name, List<ClassBox> classes) {
    }

    /** {@code stereotype} e.g. "Controller"; null for plain classes. */
    public record ClassBox(String name, String stereotype, boolean isInterface, List<Member> members) {
    }

    /** A field (type, no parameters) or a method (parameters may be empty, type may be null for void). */
    public record Member(Visibility visibility, String name, String type, boolean method, String parameters) {
    }

    public enum Visibility { PUBLIC, PRIVATE }

    public enum RelationKind { COMPOSITION, DEPENDENCY, REALIZATION, INHERITANCE }

    public record Relation(String from, String to, RelationKind kind, String label, String multiplicity) {
    }

    // ---------------------------------------------------------------- sequence diagrams

    public enum ParticipantType { ACTOR, PARTICIPANT, DATABASE, QUEUE, EXTERNAL }

    public record Participant(String id, String label, ParticipantType type) {
    }

    public record Sequence(List<Participant> participants, List<Item> items) {
    }

    public sealed interface Item permits Message, Note, Block {
    }

    public enum MessageType { CALL, REPLY, ASYNC }

    public record Message(String from, String to, String text, MessageType type) implements Item {
    }

    public record Note(String over, String text) implements Item {
    }

    public enum BlockType { ALT, OPT, LOOP, BREAK }

    /** ALT has one section per alternative; the others have exactly one section. */
    public record Block(BlockType type, List<Section> sections) implements Item {
    }

    public record Section(String label, List<Item> items) {
    }

    // ---------------------------------------------------------------- overview

    public enum NodeType { CLIENTS, SERVICE, EXTERNAL, DATABASE, QUEUE }

    public record Node(String id, String label, NodeType type) {
    }

    public record Edge(String from, String to, String label) {
    }

    public record Overview(List<Node> nodes, List<Edge> edges) {
    }
}
