package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

//variables stored in a data structure called an environment which acts as a map between variable names and values
class Environment {
    final Environment enclosing; //keeps track of the environment around it
    private final Map<String, Object> values = new HashMap<>();

    Environment() { //for global scope
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    Environment ancestor(int distance) { //returns the environment 'distance' number of enclosures away
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment;
    }

    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    //binds a name to a value
    void define(String name, Object value) {
        values.put(name, value);
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        if (enclosing != null) return enclosing.get(name); //if the variable isn't found in this environment, recursively check the enclosing ones

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) { //if the variable isn't found in this environment, recursively check the enclosing ones
            enclosing.assign(name, value);
            return;
        }

        //throw runtime error if no variable exists in the scope with this name
        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

}
