package net.realmofuz;

import java.util.Iterator;

public class StringIterator implements Iterator<Character> {
    String internal;
    int index = -1;

    public StringIterator(String internal) {
        this.internal = internal;
    }

    @Override
    public boolean hasNext() {
        return index < internal.length()-1;
    }

    public int index() {
        return index+1;
    }

    public int length() {
        return internal.length();
    }


    public void skipWhitespace() {
        while(true) {
            if(!hasNext())
                break;
            if(!Character.isWhitespace(peek()))
                break;
            next();
        }

    }

    public void skipSpaces() {
        while(true) {
            if(!(peek() == '\t' || peek() == ' '))
                break;
            next();
        }
    }

    @Override
    public Character next() {
        if(!hasNext())
            return '\n';
        return internal.charAt(++index);
    }

    public char peek() {
        if(!hasNext())
            return '\n';
        return internal.charAt(index+1);
    }
}
