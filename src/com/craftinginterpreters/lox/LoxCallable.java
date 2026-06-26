package com.craftinginterpreters.lox;

import java.util.List;

//The Java representation of any Lox object that can be called like a function i.e - functions, objects.
interface LoxCallable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments); //implementers must return the value that the call expression produces
}