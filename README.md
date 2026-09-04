# A tree-walk interpreter for the Lox Language, an educational scripting language, made using Java.

## How to use
run the src/com/craftinginterpreters/lox/Lox.java file with a text file using valid lox syntax as argument.  
To find out more about lox syntax please review the crafting interpreters book  
Note: some additions have been made to add the challenges so syntax may be slightly different. 
(For example support for break and static methods have been added).  


## How it works
Firstly, the source code is tokenized to make it easier to parse.  
The tokens are then parsed, if no syntax errors are produced then a syntax tree is produced.  
The resolver then resolves the scopes for the variables and reports any additional errors before runtime (i.e using 'this' outside of a class)  
Finally, the interpreter then travels down the resolved tree executing the code and reporting any runtime errors.  

## Features
Lox is a dynamic high-level language that supports the following:  
  -Basic arithmetic (+, -, *, /)  
  -Boolean, numbers, strings and nil data types  
  -comparison operations  
  -logical operations  
  -print statements  
  -variables  
  -String concatenation  
  -control flow (if, else, break, return)  
  -comments (single and multi-line)
  -functions (including local)  
  -Classes (with initialization, static methods and inheritance)  

## To add
The language would need the following to be useful:  
  -user input  
  -file manipulation  
  -string manipulation  
  -collections (arrays, lists, maps, etc)  
  -a math library (trig, square root, etc)  
  -a framework for users to create their own libraries

## Credits  
This project was made alongside reading the Crafting Interpreters book by Robert Nystrom  
https://craftinginterpreters.com
