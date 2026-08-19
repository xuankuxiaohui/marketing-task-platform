package com.mkt.task.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Save-time DAG: no cycle and every edge is strictly forward by seq (R11.2).
 * Reachability is publish-time (R12.3) and exposed for the property test.
 */
public final class TaskGraphValidator {

    private TaskGraphValidator() {}

    public static boolean accepts(List<GraphStep> steps, List<GraphEdge> edges) {
        return validateSave(steps, edges) == SaveResult.OK;
    }

    public static SaveResult validateSave(List<GraphStep> steps, List<GraphEdge> edges) {
        if (steps == null || steps.isEmpty()) {
            return edges == null || edges.isEmpty() ? SaveResult.OK : SaveResult.UNKNOWN_ENDPOINT;
        }
        Map<String, Integer> seqByCode = new HashMap<>();
        Set<Integer> seqs = new HashSet<>();
        for (GraphStep step : steps) {
            if (step == null || step.code() == null || step.code().isBlank()) {
                return SaveResult.DUPLICATE_CODE;
            }
            if (seqByCode.put(step.code(), step.seq()) != null) {
                return SaveResult.DUPLICATE_CODE;
            }
            if (!seqs.add(step.seq())) {
                return SaveResult.DUPLICATE_SEQ;
            }
        }
        if (edges == null || edges.isEmpty()) {
            return SaveResult.OK;
        }
        Map<String, List<String>> outgoing = new HashMap<>();
        for (GraphEdge edge : edges) {
            if (edge == null) {
                return SaveResult.UNKNOWN_ENDPOINT;
            }
            Integer fromSeq = seqByCode.get(edge.fromCode());
            Integer toSeq = seqByCode.get(edge.toCode());
            if (fromSeq == null || toSeq == null) {
                return SaveResult.UNKNOWN_ENDPOINT;
            }
            if (toSeq <= fromSeq) {
                return SaveResult.BACK_OR_CYCLE;
            }
            outgoing.computeIfAbsent(edge.fromCode(), key -> new ArrayList<>()).add(edge.toCode());
        }
        if (hasCycle(seqByCode.keySet(), outgoing)) {
            return SaveResult.BACK_OR_CYCLE;
        }
        return SaveResult.OK;
    }

    public static boolean reachableAndOpen(List<GraphStep> steps, List<GraphEdge> edges) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        if (validateSave(steps, edges) != SaveResult.OK) {
            return false;
        }
        Map<String, List<String>> outgoing = new HashMap<>();
        if (edges != null) {
            for (GraphEdge edge : edges) {
                outgoing.computeIfAbsent(edge.fromCode(), key -> new ArrayList<>()).add(edge.toCode());
            }
        }
        GraphStep start = steps.stream().min(Comparator.comparingInt(GraphStep::seq)).orElseThrow();
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start.code());
        seen.add(start.code());
        while (!queue.isEmpty()) {
            String code = queue.removeFirst();
            for (String next : outgoing.getOrDefault(code, List.of())) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (seen.size() != steps.size()) {
            return false;
        }
        int terminals = 0;
        for (GraphStep step : steps) {
            boolean hasOut = outgoing.containsKey(step.code()) && !outgoing.get(step.code()).isEmpty();
            if (hasOut) {
                continue;
            }
            terminals++;
        }
        return terminals >= 1;
    }

    private static boolean hasCycle(Set<String> nodes, Map<String, List<String>> outgoing) {
        Map<String, Integer> state = new HashMap<>();
        for (String node : nodes) {
            state.put(node, 0);
        }
        for (String node : nodes) {
            if (state.get(node) == 0 && dfsCycle(node, outgoing, state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfsCycle(String node, Map<String, List<String>> outgoing, Map<String, Integer> state) {
        state.put(node, 1);
        for (String next : outgoing.getOrDefault(node, List.of())) {
            Integer nextState = state.get(next);
            if (nextState == null) {
                continue;
            }
            if (nextState == 1) {
                return true;
            }
            if (nextState == 0 && dfsCycle(next, outgoing, state)) {
                return true;
            }
        }
        state.put(node, 2);
        return false;
    }

    public enum SaveResult {
        OK,
        DUPLICATE_CODE,
        DUPLICATE_SEQ,
        UNKNOWN_ENDPOINT,
        BACK_OR_CYCLE
    }
}
