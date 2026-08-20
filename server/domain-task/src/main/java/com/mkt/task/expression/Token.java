package com.mkt.task.expression;

record Token(TokenType type, String lexeme, int line, int column) {}
