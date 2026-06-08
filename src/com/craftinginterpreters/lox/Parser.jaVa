package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

//recursive descent Parser
class Parser {

    //sentinal class used to rewind parser
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }


    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) { //iterates through all expressions
            statements.add(declaration()); //program is made of declerations grammatically
        }

        return statements;
    }

    private Stmt statement() {
        if (match(PRINT)) return printStatement(); //statements starting with a print token are print statements
        if (match(LEFT_BRACE)) return new Stmt.Block(block()); //statements starting with { are a block statements

        return expressionStatement(); //if token does not match any kind of  statement, assume it is an expression statement
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration()); //parse statements and add them until the end of the block is reached ( } ).
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt printStatement() {
        Expr value = expression(); //parse subsequent statement after PRINT
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value); //emit the syntax tree
    }

    private Stmt expressionStatement() {
        Expr expr = expression(); //parse expression
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr); //emit syntax tree
    }


    private Expr expression() { //expressions extend equality rule
        return assignment();
    }

    private Expr assignment() {
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment(); //recursively call assignment to parse the right-hand side as if it were an expression

            if (expr instanceof Expr.Variable) { //if a valid variable target is found we produce a syntax tree that turns the "expression" into an assignment target
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target."); //don't throw as we don't need to synchronise due to this error
        }

        return expr;
    }

    //gramatically declerations are made of var decl., func decl, object decl and statements
    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement(); //if no keywords then falls through to statement
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null; // if no value specified the var is stored as null
        if (match(EQUAL)) { //if value specified
            initializer = expression(); //value stored as expression value
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }


    private Expr equality() {
        Expr expr = comparison(); //if no equality, comparison is called
        //method effectively matches an equality operator or anything of higher precidence

        while (match(BANG_EQUAL, EQUAL_EQUAL)) { //continues until current token does not contain any of the given types
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right); //creates a tree of expressions
        }

        return expr;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) { //returns true if current token matches any of the given types
                advance(); //consumes token if a match is found
                return true;
            }
        }

        return false;
    }

    private boolean check(TokenType type) { //returns true if current token matches a given type
        if (isAtEnd()) return false;
        return peek().type == type; //token not consumed if true
    }

    private Token advance() {
        if (!isAtEnd()) current++; //consumes current token
        return previous(); //returns consumed token
    }

    private boolean isAtEnd() { //checks if we have ran out of tokens to parse
        return peek().type == EOF;
    }

    private Token peek() { //returns current token
        return tokens.get(current);
    }

    private Token previous() { //returns most recently consumed token
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) { //skips tokens until a synchronisation point is found
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    private Expr comparison() { //grammar rule virtually identical to equality
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() { //grammar rule virtually identical to comparison
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() { //grammar rule virtually identical to term
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary(); //primary only called after loop as there is no left value only right
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression."); //all open brackets must be closed for valid syntax
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression."); //if none of the cases match token can't start an expression
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance(); //checks to see if token matches an expected type

        throw error(peek(), message); //if not the appropriate error is reported
    }
}