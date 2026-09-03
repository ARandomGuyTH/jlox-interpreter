package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

class Interpreter implements Expr.Visitor<Object>,
        Stmt.Visitor<Void> {
    final Environment globals = new Environment(); //global field holds a fixed reference to the outermost environment
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter() { // add native functions here
        globals.define("clock", new LoxCallable() { //defines clock variable
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0; //calls the corrisponding Java function and converts to seconds
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    private Environment environment = globals;

    private static class BreakException extends RuntimeException {}

    void resolve(Expr expr, int depth) { //tracks the number of environments from the current to find a variable's value
        locals.put(expr, depth);
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value); //if we have a return value return it
        //otherwise we return nill
        throw new Return(value); //wrap the return value in a custom exception class
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment,false);
        environment.define(stmt.name.lexeme, function); //add the function to the environment
        return null;
    }


    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee); //evaluate the callee

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument)); //evaluate all expressions in order and add them to a list
        }

        if (!(callee instanceof LoxCallable)) { //in case the callee is not valid like calling a strin i.e - "not a function"();
            throw new RuntimeError(expr.paren,
                    "Can only call functions and classes."); //we will throw our own error so the interpretter can catch and report it
        }

        LoxCallable function = (LoxCallable)callee; //cast the callee as lox callable to invoke the call method on it
        if (arguments.size() != function.arity()) { //if the number of arguments does not match the expected number of arguments
            throw new RuntimeError(expr.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + "."); //we throw an error before invoking the callable
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) { //if the object is an instance of a class
            return ((LoxInstance) object).get(expr.name);//we ask the instance for the property
        }

        throw new RuntimeError(expr.name,
                "Only instances have properties.");
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name,
                    "Only instances have fields.");
        }

        Object value = evaluate(expr.value); //evaluate the value being set
        ((LoxInstance)object).set(expr.name, value); //set attribute to this value
        return value;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException(); //break throws an exception which will get caught by the while code
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while (isTruthy(evaluate(stmt.condition))) { //while the condition is true
                execute(stmt.body); //repeatedly execute the body of the statement
            }
        } catch (BreakException exception) {
            return null;  //Break exception exits the while loop
        }
        return null;
    }

    //note we only evaluate the right value if it is non-determinate from the left
    //this means that some side effects from the right value may or may not occur depending on the left value
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left); //evaluate left expression

        if (expr.operator.type == TokenType.OR) { //if or
            if (isTruthy(left)) return left; //if the left value is true the expression will be true for or
        } else { //if and
            if (!isTruthy(left)) return left; //if the left value is false then the expression will be false for and
        }

        return evaluate(expr.right); //if the value is non-determinate from left we take the right value as the value
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) { // if truthy we execute the then branch
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) { //otherwise, if there is an else branch we execute that
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment)); //create a new environment for the block's scope
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        environment.define(stmt.name.lexeme, null); //declare class name in current environment
        // (done before runtime representation so class can be referenced in it's methods)

        Map<String, LoxFunction> statics = new HashMap<>();
        for (Stmt.Function method : stmt.statics) {
            LoxFunction function = new LoxFunction(method, environment, false);
            statics.put(method.name.lexeme, function);
        }

        LoxClass metaklass = new LoxClass(stmt.name.lexeme + "metaClass", statics, null);

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function); //wrap methods in map
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, methods, metaklass); //create LoxClass from methods and class AST node
        environment.assign(stmt.name, klass); //store
        return null;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) { //same as interpretting a variable statement
        return lookUpVariable(expr.keyword, expr);
    }

    void executeBlock(List<Stmt> statements,
                      Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement); //execute statements in the block's environment
            }
        } finally {
            this.environment = previous; //environment restored even if an error occurs
        }
    }


    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value); //evaluate right hand side to get the value

        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value); //assign the variable to it's name
        } else {
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression); //evaluate inner expression
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression); //evaluate inner expression
        System.out.println(stringify(value)); //convert and print inner expression
        return null;
    }

    //if evaluating a literal we  return the value of the literal
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    //if evaluating parenthesis we recursively evaluate that subexpression and return it
    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    // helper method sends expression back into the visitor implementation
    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) { //if variable has initializer evaluate it
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr); //get resolved distance
        if (distance != null) {
            return environment.getAt(distance, name.lexeme); //get the value
        } else { //if does not appear in the map it must be a global var
            return globals.get(name);
        }
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right); //evaluate operand

        //apply unary operator to operand
        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);

            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        // Unreachable.
        return null;
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number."); //throw lox runtime error
    }



    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left); //evaluate operands left to right
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS: //must check if concatonating or adding
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }

                if (left instanceof String &&  right instanceof Double) {
                    return (String)left + stringify(right);
                }

                if (left instanceof Double && right instanceof String) {
                    return  stringify(left) + (String)right;
                }

                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or two strings.");

            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double)right == 0) throw new RuntimeError(expr.operator,"Cannot divide by zero");
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);

        }

        // Unreachable.
        return null;
    }

    private void checkNumberOperands(Token operator,
                                     Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    //false and nil falsey, everything else truthy
    private boolean isTruthy(Object object) { //implicitly converts values to bool
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) { //lox's definition of equality
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) { //converts lox objects to strings
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) { //integers should print without decimal (despite represented as doubles)
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    //API iterates through expressions, evaluating the complete syntax tree for all expressions
    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

}
