package net.realmofuz.compile.parameters;

import net.realmofuz.parser.tree.Ast;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Represents a map of arguments and their granted names.
 * It is up to the developer of the commands to ensure this is used properly.
 * <b>No runtime checks will be made when using {@link ArgumentSet#get}</b>.
 */
public class ArgumentSet {
    HashMap<String, Ast.Value> arguments;

    public ArgumentSet(HashMap<String, Ast.Value> arguments) {
        this.arguments = arguments;
    }

    /**
     * Gets an argument by name.
     * <b>No runtime checks will be made when using {@link ArgumentSet#get}</b>.
     * @param name The name of the argument
     * @return The argument desired
     * @param <T> The type needed for the argument
     */
    @SuppressWarnings("unchecked")
    public<T extends Ast.Value> T get(String name) {
        return (T) this.arguments.get(name);
    }

    @Override
    public String toString() {
        return this.arguments.toString();
    }

    @Override
    public ArgumentSet clone() throws CloneNotSupportedException {
        ArgumentSet argumentSet = (ArgumentSet) super.clone();
        argumentSet.arguments = this.arguments;
        return argumentSet;
    }
}
