package net.realmofuz.compile.parameters;

import net.realmofuz.parser.SpanData;
import net.realmofuz.parser.tree.Ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An iterator over a list of arguments, in the form of an Ast.Value.
 */
public class ArgumentIterator implements Iterator<Ast.Value>, Cloneable {
    Ast.Command astCommand;
    List<Ast.Value> internal;
    int index = -1;

    public ArgumentIterator(List<Ast.Value> values, Ast.Command command) {
        this.internal = values;
        this.astCommand = command;
    }

    @Override
    public boolean hasNext() {
        return index < internal.size() - 1;
    }

    @Override
    public Ast.Value next() {
        return internal.get(++index);
    }

    public int length() {
        return internal.size();
    }

    public SpanData lastSpan() {
        if(index == -1)
            try {
                return internal.getFirst().span();
            } catch (NoSuchElementException ex) {
                return astCommand.span();
            }

        return internal.get(index).span();
    }

    public ArgumentIterator copy() {
        var ai = new ArgumentIterator(new ArrayList<>(this.internal), this.astCommand);
        return ai;
    }
}
