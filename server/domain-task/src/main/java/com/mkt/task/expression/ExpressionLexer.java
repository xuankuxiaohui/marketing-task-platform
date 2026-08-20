package com.mkt.task.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tokenizes the closed DSL. Does not interpret {@code \\u} escapes (M-12). */
final class ExpressionLexer {

    private final String source;
    private final int length;
    private int index;
    private int line = 1;
    private int column = 1;

    ExpressionLexer(String source) {
        this.source = source;
        this.length = source.length();
    }

    List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            Token token = next();
            tokens.add(token);
            if (token.type() == TokenType.EOF) {
                return tokens;
            }
        }
    }

    private Token next() {
        skipWhitespace();
        if (index >= length) {
            return new Token(TokenType.EOF, "", line, column);
        }
        int startLine = line;
        int startCol = column;
        char c = peek();
        if (c == '\'') {
            return string(startLine, startCol);
        }
        if (c == '-' || isDigit(c)) {
            return number(startLine, startCol);
        }
        if (isIdentStart(c)) {
            return identOrKeyword(startLine, startCol);
        }
        return operator(startLine, startCol);
    }

    private Token string(int startLine, int startCol) {
        advance();
        StringBuilder body = new StringBuilder();
        while (index < length) {
            char c = peek();
            if (c == '\'') {
                advance();
                return new Token(TokenType.STRING, body.toString(), startLine, startCol);
            }
            if (c == '\\') {
                advance();
                if (index >= length) {
                    throw error(startLine, startCol, "未闭合的字符串");
                }
                body.append('\\').append(advance());
                continue;
            }
            if (c == '\n') {
                throw error(startLine, startCol, "未闭合的字符串");
            }
            body.append(advance());
        }
        throw error(startLine, startCol, "未闭合的字符串");
    }

    private Token number(int startLine, int startCol) {
        int begin = index;
        if (peek() == '-') {
            advance();
            if (index >= length || !isDigit(peek())) {
                throw error(startLine, startCol, "非法运算符");
            }
        }
        while (index < length && isDigit(peek())) {
            advance();
        }
        if (index < length) {
            char next = peek();
            if (next == '.' || next == 'e' || next == 'E') {
                throw error(startLine, startCol, "字面量仅允许整数与单引号字符串");
            }
            if (isIdentStart(next)) {
                throw error(startLine, startCol, "非法数字字面量");
            }
        }
        String lexeme = source.substring(begin, index);
        try {
            Long.parseLong(lexeme);
        } catch (NumberFormatException ex) {
            throw error(startLine, startCol, "整数字面量超出 long 域");
        }
        return new Token(TokenType.NUMBER, lexeme, startLine, startCol);
    }

    private Token identOrKeyword(int startLine, int startCol) {
        int begin = index;
        advance();
        while (index < length && isIdentPart(peek())) {
            advance();
        }
        String raw = source.substring(begin, index);
        if (index < length && peek() == '-') {
            int savedIndex = index;
            int savedLine = line;
            int savedCol = column;
            advance();
            if (matchIgnoreCase("in")) {
                return new Token(TokenType.NOT_IN, raw + "-in", startLine, startCol);
            }
            index = savedIndex;
            line = savedLine;
            column = savedCol;
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "AND" -> new Token(TokenType.AND, raw, startLine, startCol);
            case "OR" -> new Token(TokenType.OR, raw, startLine, startCol);
            case "NOT" -> notOrNotIn(raw, startLine, startCol);
            case "IN" -> new Token(TokenType.IN, raw, startLine, startCol);
            default -> new Token(TokenType.IDENT, raw, startLine, startCol);
        };
    }

    private Token notOrNotIn(String raw, int startLine, int startCol) {
        skipWhitespace();
        if (matchIgnoreCase("in")) {
            return new Token(TokenType.NOT_IN, raw + " in", startLine, startCol);
        }
        return new Token(TokenType.NOT, raw, startLine, startCol);
    }

    private Token operator(int startLine, int startCol) {
        char c = advance();
        return switch (c) {
            case '(' -> new Token(TokenType.LPAREN, "(", startLine, startCol);
            case ')' -> new Token(TokenType.RPAREN, ")", startLine, startCol);
            case ',' -> new Token(TokenType.COMMA, ",", startLine, startCol);
            case '=' -> {
                if (index < length && peek() == '=') {
                    advance();
                    yield new Token(TokenType.EQ, "==", startLine, startCol);
                }
                yield new Token(TokenType.EQ, "=", startLine, startCol);
            }
            case '!' -> {
                if (index < length && peek() == '=') {
                    advance();
                    yield new Token(TokenType.NE, "!=", startLine, startCol);
                }
                throw error(startLine, startCol, "逻辑运算符仅允许 AND OR NOT");
            }
            case '>' -> {
                if (index < length && peek() == '=') {
                    advance();
                    yield new Token(TokenType.GE, ">=", startLine, startCol);
                }
                yield new Token(TokenType.GT, ">", startLine, startCol);
            }
            case '<' -> {
                if (index < length && peek() == '=') {
                    advance();
                    yield new Token(TokenType.LE, "<=", startLine, startCol);
                }
                yield new Token(TokenType.LT, "<", startLine, startCol);
            }
            case '&', '|' -> throw error(startLine, startCol, "逻辑运算符仅允许 AND OR NOT");
            case '.' -> throw error(startLine, startCol, "禁止方法调用与属性访问");
            case '"' -> throw error(startLine, startCol, "字符串字面量必须使用单引号");
            default -> throw error(startLine, startCol, "非法字符");
        };
    }

    private boolean matchIgnoreCase(String expected) {
        if (index + expected.length() > length) {
            return false;
        }
        String slice = source.substring(index, index + expected.length());
        if (!slice.equalsIgnoreCase(expected)) {
            return false;
        }
        int after = index + expected.length();
        if (after < length && isIdentPart(source.charAt(after))) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            advance();
        }
        return true;
    }

    private void skipWhitespace() {
        while (index < length) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
                continue;
            }
            if (c == '\n') {
                advance();
                continue;
            }
            return;
        }
    }

    private char peek() {
        return source.charAt(index);
    }

    private char advance() {
        char c = source.charAt(index++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    private static ExpressionCompileException error(int line, int column, String reason) {
        return new ExpressionCompileException(ExpressionError.at(line, column, reason));
    }
}
