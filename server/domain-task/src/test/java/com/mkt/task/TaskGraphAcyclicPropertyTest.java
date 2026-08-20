package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.domain.GraphEdge;
import com.mkt.task.domain.GraphStep;
import com.mkt.task.domain.TaskGraphValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** R11.2: validator accepts iff the edge set is acyclic and strictly forward by seq. */
class TaskGraphAcyclicPropertyTest {

    private static final Logger log = LoggerFactory.getLogger(TaskGraphAcyclicPropertyTest.class);

    @Property(tries = 200)
    void acceptsIffForwardAcyclic(@ForAll("graphs") GraphCase graph) {
        log.debug("graph steps={} edges={}", graph.steps().size(), graph.edges().size());
        boolean accepted = TaskGraphValidator.accepts(graph.steps(), graph.edges());
        boolean expected = forwardAcyclic(graph.steps(), graph.edges());
        assertThat(accepted).isEqualTo(expected);
        if (accepted) {
            boolean reachable = TaskGraphValidator.reachableAndOpen(graph.steps(), graph.edges());
            assertThat(reachable).isEqualTo(allReachableFromMinSeq(graph.steps(), graph.edges()));
        }
    }

    @Provide
    Arbitrary<GraphCase> graphs() {
        Arbitrary<Integer> size = Arbitraries.integers().between(1, 8);
        return size.flatMap(n -> {
            List<GraphStep> steps = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                steps.add(new GraphStep("s" + i, i));
            }
            List<GraphEdge> possible = new ArrayList<>();
            for (int from = 1; from <= n; from++) {
                for (int to = 1; to <= n; to++) {
                    if (from != to) {
                        possible.add(new GraphEdge("s" + from, "s" + to));
                    }
                }
            }
            return Arbitraries.subsetOf(possible).map(set -> new GraphCase(steps, List.copyOf(set)));
        });
    }

    private static boolean forwardAcyclic(List<GraphStep> steps, List<GraphEdge> edges) {
        int[] seq = new int[steps.size() + 1];
        for (GraphStep step : steps) {
            seq[Integer.parseInt(step.code().substring(1))] = step.seq();
        }
        for (GraphEdge edge : edges) {
            int from = Integer.parseInt(edge.fromCode().substring(1));
            int to = Integer.parseInt(edge.toCode().substring(1));
            if (seq[to] <= seq[from]) {
                return false;
            }
        }
        return !hasCycle(steps.size(), edges);
    }

    private static boolean hasCycle(int n, List<GraphEdge> edges) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            adj.add(new ArrayList<>());
        }
        for (GraphEdge edge : edges) {
            int from = Integer.parseInt(edge.fromCode().substring(1));
            int to = Integer.parseInt(edge.toCode().substring(1));
            adj.get(from).add(to);
        }
        int[] state = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            if (state[i] == 0 && dfs(i, adj, state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfs(int node, List<List<Integer>> adj, int[] state) {
        state[node] = 1;
        for (int next : adj.get(node)) {
            if (state[next] == 1) {
                return true;
            }
            if (state[next] == 0 && dfs(next, adj, state)) {
                return true;
            }
        }
        state[node] = 2;
        return false;
    }

    private static boolean allReachableFromMinSeq(List<GraphStep> steps, List<GraphEdge> edges) {
        GraphStep start = steps.get(0);
        Set<String> seen = new HashSet<>();
        ArrayDequeBfs(start.code(), edges, seen);
        return seen.size() == steps.size();
    }

    private static void ArrayDequeBfs(String start, List<GraphEdge> edges, Set<String> seen) {
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            String code = queue.removeFirst();
            for (GraphEdge edge : edges) {
                if (edge.fromCode().equals(code) && seen.add(edge.toCode())) {
                    queue.add(edge.toCode());
                }
            }
        }
    }

    record GraphCase(List<GraphStep> steps, List<GraphEdge> edges) {}
}
