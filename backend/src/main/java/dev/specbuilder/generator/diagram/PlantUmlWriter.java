package dev.specbuilder.generator.diagram;

import dev.specbuilder.generator.diagram.DiagramModel.*;

import java.util.List;

/**
 * Writes diagrams in PlantUML syntax. Class and component diagrams use the built-in Smetana
 * layout, so rendering needs no Graphviz installation.
 */
public final class PlantUmlWriter {

    private PlantUmlWriter() {
    }

    public static String classes(ClassDiagram d, String title) {
        StringBuilder sb = header(title, true);
        // Left to right keeps Smetana from laying packages out in one very wide row.
        sb.append("left to right direction\nhide empty members\nskinparam classAttributeIconSize 0\n");
        for (DiagramModel.Package p : d.packages()) {
            if (p.classes().isEmpty()) continue;
            sb.append("package ").append(p.name()).append(" {\n");
            for (ClassBox c : p.classes()) {
                sb.append("  ").append(c.isInterface() ? "interface " : "class ").append(c.name());
                if (c.stereotype() != null && !c.isInterface()) sb.append(" <<").append(c.stereotype()).append(">>");
                sb.append(" {\n");
                for (Member m : c.members()) {
                    sb.append("    ").append(m.visibility() == Visibility.PUBLIC ? "+" : "-").append(m.name());
                    if (m.method()) sb.append("(").append(m.parameters() == null ? "" : m.parameters()).append(")");
                    if (m.type() != null) sb.append(" : ").append(m.type());
                    sb.append("\n");
                }
                sb.append("  }\n");
            }
            sb.append("}\n");
        }
        for (Relation r : d.relations()) {
            String arrow = switch (r.kind()) {
                case COMPOSITION -> "*--";
                case DEPENDENCY -> "..>";
                case REALIZATION -> "..|>";
                case INHERITANCE -> "--|>";
            };
            sb.append(r.from()).append(" ").append(arrow).append(" ");
            if (r.multiplicity() != null) sb.append("\"").append(r.multiplicity()).append("\" ");
            sb.append(r.to());
            if (r.label() != null) sb.append(" : ").append(text(r.label()));
            sb.append("\n");
        }
        return sb.append("@enduml\n").toString();
    }

    public static String sequence(Sequence s, String title) {
        StringBuilder sb = header(title, false);
        sb.append("autonumber\n");
        for (Participant p : s.participants()) {
            String kind = switch (p.type()) {
                case ACTOR -> "actor";
                case PARTICIPANT -> "participant";
                case DATABASE -> "database";
                case QUEUE -> "queue";
                case EXTERNAL -> "boundary";
            };
            sb.append(kind).append(" \"").append(quoted(p.label())).append("\" as ").append(p.id()).append("\n");
        }
        items(sb, s.items(), "");
        return sb.append("@enduml\n").toString();
    }

    private static void items(StringBuilder sb, List<Item> items, String indent) {
        for (Item item : items) {
            switch (item) {
                case Message m -> sb.append(indent).append(m.from()).append(switch (m.type()) {
                    case CALL -> " -> ";
                    case REPLY -> " --> ";
                    case ASYNC -> " ->> ";
                }).append(m.to()).append(" : ").append(text(m.text())).append("\n");
                case Note n -> sb.append(indent).append("note over ").append(n.over()).append(" : ").append(text(n.text())).append("\n");
                case Block b -> {
                    String keyword = switch (b.type()) {
                        case ALT -> "alt";
                        case OPT -> "opt";
                        case LOOP -> "loop";
                        case BREAK -> "break";
                    };
                    for (int i = 0; i < b.sections().size(); i++) {
                        Section section = b.sections().get(i);
                        sb.append(indent).append(i == 0 ? keyword : "else").append(" ").append(text(section.label())).append("\n");
                        items(sb, section.items(), indent + "  ");
                    }
                    sb.append(indent).append("end\n");
                }
            }
        }
    }

    public static String overview(Overview o, String title) {
        StringBuilder sb = header(title, true);
        sb.append("left to right direction\n");
        for (Node n : o.nodes()) {
            String kind = switch (n.type()) {
                case CLIENTS -> "actor";
                case SERVICE -> "component";
                case EXTERNAL -> "cloud";
                case DATABASE -> "database";
                case QUEUE -> "queue";
            };
            sb.append(kind).append(" \"").append(quoted(n.label())).append("\" as ").append(n.id()).append("\n");
        }
        for (Edge e : o.edges()) {
            sb.append(e.from()).append(" --> ").append(e.to());
            if (e.label() != null) sb.append(" : ").append(text(e.label()));
            sb.append("\n");
        }
        return sb.append("@enduml\n").toString();
    }

    private static StringBuilder header(String title, boolean smetana) {
        StringBuilder sb = new StringBuilder("@startuml\n");
        if (smetana) sb.append("!pragma layout smetana\n");
        sb.append("title ").append(text(title)).append("\n");
        return sb;
    }

    /** Single-line text; a leading/trailing quote or tilde is the only syntax that needs care. */
    static String text(String s) {
        return s.replace("\r", "").replace("\n", " ").replace("~", "~~");
    }

    private static String quoted(String s) {
        return text(s).replace("\"", "'");
    }
}
