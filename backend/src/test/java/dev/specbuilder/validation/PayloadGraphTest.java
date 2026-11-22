package dev.specbuilder.validation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadGraphTest {

    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);

    @Test
    void findsNoCycleInATree() {
        PayloadGraph graph = new PayloadGraph(Map.of(a, Set.of(b, c), b, Set.of(c), c, Set.of()));
        assertThat(graph.anyCycle()).isEmpty();
        assertThat(graph.cycleThrough(a)).isEmpty();
    }

    @Test
    void findsASelfReference() {
        PayloadGraph graph = new PayloadGraph(Map.of(a, Set.of(a)));
        assertThat(graph.cycleThrough(a)).contains(java.util.List.of(a, a));
    }

    @Test
    void findsALongerCycleAndReportsThePath() {
        PayloadGraph graph = new PayloadGraph(Map.of(a, Set.of(b), b, Set.of(c), c, Set.of(a)));
        assertThat(graph.cycleThrough(a)).contains(java.util.List.of(a, b, c, a));
        assertThat(graph.anyCycle()).isPresent();
    }

    @Test
    void aCycleElsewhereDoesNotCountForANodeOutsideIt() {
        PayloadGraph graph = new PayloadGraph(Map.of(a, Set.of(b), b, Set.of(c), c, Set.of(b)));
        assertThat(graph.cycleThrough(a)).isEmpty();
        assertThat(graph.cycleThrough(b)).contains(java.util.List.of(b, c, b));
    }
}
