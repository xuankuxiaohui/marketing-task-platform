package com.mkt.task.expression;

/** Immutable compile product reused at runtime (design §5.10). */
public final class CompiledExpression {

    private final String source;
    private final ExprNode root;
    private final int nodeCount;

    CompiledExpression(String source, ExprNode root, int nodeCount) {
        this.source = source;
        this.root = root;
        this.nodeCount = nodeCount;
    }

    public String source() {
        return source;
    }

    public int nodeCount() {
        return nodeCount;
    }

    ExprNode root() {
        return root;
    }
}
