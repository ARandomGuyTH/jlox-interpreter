package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;

    private final boolean isInitializer;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.isInitializer = isInitializer;
        this.closure = closure;
        this.declaration = declaration;
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure); //create a new environment inside the closure
        environment.define("this", instance); //define this as the given instance and bind it to the new environment
        return new LoxFunction(declaration, environment, isInitializer);
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }

    @Override
    public Object call(Interpreter interpreter,
                       List<Object> arguments) {
        Environment environment = new Environment(closure); //create a new environment envlosed by the closure environment
        for (int i = 0; i < declaration.params.size(); i++) { //add all the parameters to the environment
            environment.define(declaration.params.get(i).lexeme,
                    arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) { //when we catch the return statement we stop executing
            if (isInitializer) return closure.getAt(0, "this"); //if in initializer when returning empty we should default to 'this' not 'nil'
            return returnValue.value; //and return the corresponding return value for the return statement
        }

        if (isInitializer) return closure.getAt(0, "this"); //initializer returns this by default
        return null; //return value is nill by default
    }
}
