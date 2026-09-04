package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable { //calling a Lox class is used to create a new instance of the class
    final String name;
    final LoxClass superclass;
    private final Map<String, LoxFunction> methods;

    LoxClass(String name, LoxClass superclass, Map<String, LoxFunction> methods, LoxClass metaklass) {
        super(metaklass); //every class instance is an instance of a metaclass
        this.superclass = superclass;
        this.name = name;
        this.methods = methods; //methods stored in class but accessed through instances
    }

    LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) { //current subclass searched before super
            return methods.get(name);
        }

        if (superclass != null) { //recurse up parent classes until found
            return superclass.findMethod(name);
        }

        return null;
    }


    //instantiates a new LoxInstance for this class and returns it
    @Override
    public Object call(Interpreter interpreter,
                       List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if (initializer != null) { //if an initializer is defined we bind it and invoke it
            initializer.bind(instance).call(interpreter, arguments);
        }

        return instance;
    }

    @Override
    public int arity() { //returns the number of arguments to pass into the constructor
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public String toString() {
        return name;
    }
}
