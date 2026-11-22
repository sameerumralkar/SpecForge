package dev.specbuilder.validation;

import java.util.*;

/** Finds reference cycles between payloads (A has a field of type B, B has a field of type A, ...). */
public final class PayloadGraph {

    private final Map<UUID, Set<UUID>> edges;

    public PayloadGraph(Map<UUID, Set<UUID>> edges) {
        this.edges = edges;
    }

    /** The first cycle through {@code start}, as a list of ids that begins and ends with {@code start}. */
    public Optional<List<UUID>> cycleThrough(UUID start) {
        Deque<UUID> path = new ArrayDeque<>();
        path.addLast(start);
        return search(start, start, path, new HashSet<>());
    }

    private Optional<List<UUID>> search(UUID start, UUID node, Deque<UUID> path, Set<UUID> visited) {
        for (UUID next : sorted(edges.getOrDefault(node, Set.of()))) {
            if (next.equals(start)) {
                List<UUID> cycle = new ArrayList<>(path);
                cycle.add(start);
                return Optional.of(cycle);
            }
            if (visited.add(next)) {
                path.addLast(next);
                Optional<List<UUID>> found = search(start, next, path, visited);
                if (found.isPresent()) {
                    return found;
                }
                path.removeLast();
            }
        }
        return Optional.empty();
    }

    /** Any cycle in the graph; nodes are tried in a stable order so the answer is repeatable. */
    public Optional<List<UUID>> anyCycle() {
        for (UUID node : sorted(edges.keySet())) {
            Optional<List<UUID>> cycle = cycleThrough(node);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private static List<UUID> sorted(Collection<UUID> ids) {
        return ids.stream().sorted().toList();
    }
}
