package com.mkt.task.expression;

import java.util.List;

sealed interface ExprNode permits
        ExprNode.Literal,
        ExprNode.Call,
        ExprNode.Unary,
        ExprNode.Binary,
        ExprNode.In,
        ExprNode.Group {

    int line();

    int column();

    record Literal(Object value, int line, int column) implements ExprNode {}

    record Call(String name, List<ExprNode> args, int line, int column) implements ExprNode {}

    record Unary(TokenType op, ExprNode operand, int line, int column) implements ExprNode {}

    record Binary(TokenType op, ExprNode left, ExprNode right, int line, int column) implements ExprNode {}

    record In(boolean negated, ExprNode left, List<ExprNode> values, int line, int column) implements ExprNode {}

    record Group(ExprNode inner, int line, int column) implements ExprNode {}
}
