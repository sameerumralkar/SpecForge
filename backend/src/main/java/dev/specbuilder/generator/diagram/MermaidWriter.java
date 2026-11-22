package dev.specbuilder.generator.diagram;

import dev.specbuilder.generator.diagram.DiagramModel.*;

import java.util.List;

/** Writes diagrams in Mermaid syntax. */
public final class MermaidWriter {

    private MermaidWriter() {
    }

    public static String classes(ClassDiagram d) {
        StringBuilder sb = new StringBuilder("classDiagram\n  direction LR\n");
        for (DiagramModel.Package p : d.packages()) {
            if (p.classes().isEmpty()) continue;
            sb.append("  namespace ").append(p.name()).append(" {\n");
            for (ClassBox c : p.classes()) {
                sb.append("    class ").append(c.name()).append(" {\n");
                if (c.isInterface()) sb.append("      <<interface>>\n");
                else if (c.stereotype() != null) sb.append("      <<").append(c.stereotype()).append(">>\n");
                for (Member m : c.members()) {
                    sb.append("      ").append(m.visibility() == Visibility.PUBLIC ? "+" : "-");
                    if (m.method()) {
                        sb.append(m.name()).append("(").append(generic(m.parameters() == null ? "" : m.parameters())).append(")");
                        if (m.type() != null) sb.append(" ").append(generic(m.type()));
                    } else {
                        sb.append(generic(m.type())).append(" ").append(m.name());
                    }
                    sb.append("\n");
                }
                sb.append("    }\n");
            }
            sb.append("  }\n");
        }
        for (Relation r : d.relations()) {
            String arrow = switch (r.kind()) {
                case COMPOSITION -> "*--";
                case DEPENDENCY -> "..>";
                case REALIZATION -> "..|>";
                case INHERITANCE -> "--|>";
            };
            sb.append("  ").append(r.from()).append(" ").append(arrow).append(" ");
            if (r.multiplicity() != null) sb.append("\"").append(r.multiplicity()).append("\" ");
            sb.append(r.to());
            if (r.label() != null) sb.append(" : ").append(text(r.label()));
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String sequence(Sequence s) {
        StringBuilder sb = new StringBuilder("sequenceDiagram\n  autonumber\n");
        for (Participant p : s.participants()) {
            sb.append("  ").append(p.type() == ParticipantType.ACTOR ? "actor " : "participant ").append(p.id())
                    .append(" as ").append(text(p.label())).append("\n");
        }
        items(sb, s.items(), "  ");
        return sb.toString();
    }

    private static void items(StringBuilder sb, List<Item> items, String indent) {
        for (Item item : items) {
            switch (item) {
                case Message m -> sb.append(indent).append(m.from()).append(switch (m.type()) {
                    case CALL -> "->>";
                    case REPLY -> "-->>";
                    case ASYNC -> "-)";
                }).append(m.to()).append(": ").append(text(m.text())).append("\n");
                case Note n -> sb.append(indent).append("Note over ").append(n.over()).append(": ").append(text(n.text())).append("\n");
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
                        if (section.items().isEmpty()) {
                            // Mermaid needs at least one statement per section.
                            sb.append(indent).append("  Note over ").append(firstParticipant(b)).append(": ").append(text(section.label())).append("\n");
                        }
                        items(sb, section.items(), indent + "  ");
                    }
                    sb.append(indent).append("end\n");
                }
            }
        }
    }

    private static String firstParticipant(Block b) {
        for (Section s : b.sections()) {
            for (Item i : s.items()) {
                if (i instanceof Message m) return m.from();
                if (i instanceof Note n) return n.over();
            }
        }
        return "self";
    }

    public static String overview(Overview o) {
        StringBuilder sb = new StringBuilder("flowchart LR\n");
        for (Node n : o.nodes()) {
            String label = "\"" + text(n.label()) + "\"";
            sb.append("  ").append(n.id()).append(switch (n.type()) {
                case CLIENTS -> "([" + label + "])";
                case SERVICE -> "[" + label + "]";
                case EXTERNAL -> "[[" + label + "]]";
                case DATABASE -> "[(" + label + ")]";
                case QUEUE -> ">" + label + "]";
            }).append("\n");
        }
        for (Edge e : o.edges()) {
            sb.append("  ").append(e.from()).append(" -->");
            if (e.label() != null) sb.append("|\"").append(text(e.label())).append("\"|");
            sb.append(" ").append(e.to()).append("\n");
        }
        return sb.toString();
    }

    /** Generic types use ~ in Mermaid class diagrams: List~OrderItem~. */
    private static String generic(String type) {
        return type.replace("<", "~").replace(">", "~");
    }

    /** Mermaid text: entity codes for characters that would otherwise be read as syntax or HTML, in one pass. */
    static String text(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '#' -> sb.append("#35;");
                case ';' -> sb.append("#59;");
                case '"' -> sb.append("#quot;");
                case '<' -> sb.append("#lt;");
                case '>' -> sb.append("#gt;");
                case '\n', '\r' -> sb.append(' ');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
