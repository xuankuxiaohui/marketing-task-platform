package com.mkt.task.expression;

import java.util.ArrayList;
import java.util.List;

/** Recursive-descent parser for the closed DSL (design §5.10). */
final class ExpressionParser {

    private final List<Token> tokens;
    private int index;
    private int nodes;

    ExpressionParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    ExprNode parse() {
        ExprNode root = or();
        if (peek().type() != TokenType.EOF) {
            throw error(peek(), "表达式末尾存在多余内容");
        }
        return root;
    }

    int nodeCount() {
        return nodes;
    }

    private ExprNode or() {
        ExprNode left = and();
        while (peek().type() == TokenType.OR) {
            Token op = advance();
            left = binary(TokenType.OR, left, and(), op);
        }
        return left;
    }

    private ExprNode and() {
        ExprNode left = not();
        while (peek().type() == TokenType.AND) {
            Token op = advance();
            left = binary(TokenType.AND, left, not(), op);
        }
        return left;
    }

    private ExprNode not() {
        if (peek().type() == TokenType.NOT) {
            Token op = advance();
            return count(new ExprNode.Unary(TokenType.NOT, not(), op.line(), op.column()));
        }
        return comparison();
    }

    private ExprNode comparison() {
        ExprNode left = primary();
        Token op = peek();
        return switch (op.type()) {
            case EQ, NE, GT, GE, LT, LE -> {
                advance();
                yield binary(op.type(), left, primary(), op);
            }
            case IN -> {
                advance();
                yield count(new ExprNode.In(false, left, list(), op.line(), op.column()));
            }
            case NOT_IN -> {
                advance();
                yield count(new ExprNode.In(true, left, list(), op.line(), op.column()));
            }
            default -> left;
        };
    }

    private ExprNode primary() {
        Token token = peek();
        return switch (token.type()) {
            case STRING -> {
                advance();
                yield count(new ExprNode.Literal(token.lexeme(), token.line(), token.column()));
            }
            case NUMBER -> {
                advance();
                yield count(new ExprNode.Literal(Long.parseLong(token.lexeme()), token.line(), token.column()));
            }
            case IDENT -> call(token);
            case LPAREN -> {
                advance();
                ExprNode inner = or();
                expect(TokenType.RPAREN, "缺少右括号");
                yield count(new ExprNode.Group(inner, token.line(), token.column()));
            }
            default -> throw error(token, "语法错误");
        };
    }

    private ExprNode call(Token name) {
        advance();
        if (peek().type() != TokenType.LPAREN) {
            throw error(name, "禁止变量引用");
        }
        if (!WhitelistFunctions.known(name.lexeme())) {
            throw error(name, "函数不在白名单");
        }
        advance();
        List<ExprNode> args = new ArrayList<>();
        if (peek().type() != TokenType.RPAREN) {
            args.add(primary());
            while (peek().type() == TokenType.COMMA) {
                advance();
                args.add(primary());
            }
        }
        expect(TokenType.RPAREN, "函数调用缺少右括号");
        int arity = WhitelistFunctions.arity(name.lexeme());
        if (args.size() != arity) {
            throw error(name, "函数参数个数不匹配");
        }
        if (WhitelistFunctions.HAS_TAG.equals(name.lexeme()) || WhitelistFunctions.IN_CROWD.equals(name.lexeme())) {
            requireStringArg(name, args.get(0));
        }
        if (WhitelistFunctions.REGISTER_WITHIN_DAYS.equals(name.lexeme())) {
            requireIntArg(name, args.get(0));
        }
        return count(new ExprNode.Call(name.lexeme(), List.copyOf(args), name.line(), name.column()));
    }

    private List<ExprNode> list() {
        expect(TokenType.LPAREN, "in / not-in 需要括号列表");
        List<ExprNode> values = new ArrayList<>();
        if (peek().type() != TokenType.RPAREN) {
            values.add(primary());
            while (peek().type() == TokenType.COMMA) {
                advance();
                values.add(primary());
            }
        }
        expect(TokenType.RPAREN, "列表缺少右括号");
        return List.copyOf(values);
    }

    private static void requireStringArg(Token name, ExprNode arg) {
        if (arg instanceof ExprNode.Literal literal && literal.value() instanceof String) {
            return;
        }
        throw error(name, "函数参数类型不匹配");
    }

    private static void requireIntArg(Token name, ExprNode arg) {
        if (arg instanceof ExprNode.Literal literal && literal.value() instanceof Long) {
            return;
        }
        throw error(name, "函数参数类型不匹配");
    }

    private ExprNode binary(TokenType op, ExprNode left, ExprNode right, Token token) {
        return count(new ExprNode.Binary(op, left, right, token.line(), token.column()));
    }

    private ExprNode count(ExprNode node) {
        nodes++;
        if (nodes > ExpressionLimits.MAX_NODES) {
            throw new ExpressionCompileException(
                    ExpressionError.at(node.line(), node.column(), "表达式节点数超过 " + ExpressionLimits.MAX_NODES));
        }
        return node;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token advance() {
        Token token = peek();
        if (token.type() != TokenType.EOF) {
            index++;
        }
        return token;
    }

    private void expect(TokenType type, String reason) {
        if (peek().type() != type) {
            throw error(peek(), reason);
        }
        advance();
    }

    private static ExpressionCompileException error(Token token, String reason) {
        return new ExpressionCompileException(ExpressionError.at(token.line(), token.column(), reason));
    }
}
