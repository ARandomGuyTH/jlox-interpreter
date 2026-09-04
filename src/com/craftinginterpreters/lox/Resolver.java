package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE; //tracks whether we are inside a function decleration

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    private ClassType currentClass = ClassType.NONE;

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) { //walks a list of statements resolving each one
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>()); //pushes the new scope onto the stack
    }

    private void endScope() {
        scopes.pop(); //pops a scope from the stack
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;

        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) { //checks if variable attempting to be declared has already been declared
            Lox.error(name,
                    "Already a variable with this name in this scope.");
        }
        scope.put(name.lexeme, false); // add the variable to the innermost scope
        //mark it as not ready by binding it to false
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().put(name.lexeme, true); //set variable to true to mark it as ready
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) { //start at the innermost scope and work outwards
            if (scopes.get(i).containsKey(name.lexeme)) { //if we find a matching variable resolve it
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        beginScope(); //create new scope for the body
        for (Token param : function.params) {
            declare(param); //bind all variables for each function parameter
            define(param);
        }
        resolve(function.body); //traverse the body
        endScope();
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope(); //begins new scope
        resolve(stmt.statements); //traverses the statements inside the block
        endScope(); //discards the scope
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass; //store previous value of current class
        currentClass = ClassType.CLASS; //update currentclass to show we are inside a class

        declare(stmt.name);
        define(stmt.name);

        //check for if a class has itself as it's super and throw error if so
        if (stmt.superclass != null &&
                stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
            Lox.error(stmt.superclass.name,
                    "A class can't inherit from itself.");
        }

        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS; //flag shows we can call super
            resolve(stmt.superclass);
        }

        if (stmt.superclass != null) { //create a scope around the methods defining super
            beginScope();
            scopes.peek().put("super", true);
        }

        beginScope();
        scopes.peek().put("this", true); //declare 'this' in scope just outside method body

        for (Stmt.Function staticMethod : stmt.statics) {
            FunctionType declaration = FunctionType.METHOD;
            resolveFunction(staticMethod, declaration);
        }

        for (Stmt.Function method : stmt.methods) { //iterate through methods
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER; //update to show we are inside an intializer
            }
            resolveFunction(method, declaration); //resolve each method
        }

        endScope();

        if (stmt.superclass != null) endScope();

        currentClass = enclosingClass; //return to old value

        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        if (currentClass == ClassType.NONE) {//check if we are in a class
            Lox.error(expr.keyword,
                    "Can't use 'super' outside of a class.");
        } else if (currentClass != ClassType.SUBCLASS) {//check if current class has a parent class
            Lox.error(expr.keyword,
                    "Can't use 'super' in a class with no superclass.");
        }

        resolveLocal(expr, expr.keyword); //resolved same as variable
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) { //'this' can only be used inside a class
            Lox.error(expr.keyword,
                    "Can't use 'this' outside of a class.");
            return null;
        }
        resolveLocal(expr, expr.keyword); //resolve like a variable using this as the keyword
        return null;
    }



    @Override
    public Void visitVarStmt(Stmt.Var stmt) { //we split var into two steps - declaring the variable then defining
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }


    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() &&
                scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) { //check to see if variable is being accessed in it's own initialiser
            Lox.error(expr.name,
                    "Can't read local variable in its own initializer."); // if declared but not defined we report the error
        }

        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value); //resolve expression for assigned value
        resolveLocal(expr, expr.name); //resolve the variable it is being assigned to
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name); //function name is bound to the scope it is declared
        define(stmt.name); //define the name so functions can recursively call themselves

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch); //both branches resolved
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) { //return should always be inside a function
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }


        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) { //can't return inside an initializer
                Lox.error(stmt.keyword,
                        "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }

        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition); //resolve both condition and body
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) { //resolve all arguments
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left); //resolve both expressions
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }


}
