package net.realmofuz.parser;

import net.realmofuz.StringIterator;
import net.realmofuz.compile.contexts.CompileError;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.util.Logger;
import net.realmofuz.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;

public class Parser {
    StringIterator iterator;
    String fileName;

    public Parser(String str, String fileName) {
        this.iterator = new StringIterator(str);
        this.fileName = fileName;
    }

    public String parseIdentifier() {
        var sb = new StringBuilder();
        while ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-/:_".contains(String.valueOf(iterator.peek()))) {
            sb.append(iterator.next());
        }
        if(sb.toString().startsWith("__")) {
            throw new CompileError.ReservedIdentifier(
                sb.toString(),
                new SpanData(fileName, iterator.index())
            );
        }
        return sb.toString();
    }

    public String parseSpecialIdentifier() {
        var sb = new StringBuilder();
        while ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-/:+-*=%_".contains(String.valueOf(iterator.peek()))) {
            sb.append(iterator.next());
        }
        if(sb.toString().startsWith("__")) {
            throw new CompileError.ReservedIdentifier(
                sb.toString(),
                new SpanData(fileName, iterator.index())
            );
        }
        return sb.toString();
    }

    public Ast.Value parseIdentifierValue() {
        var id = parseSpecialIdentifier();
        return switch (id) {
            case "true" -> new Ast.Value.Boolean(true, new SpanData(fileName, iterator.index()));
            case "false" -> new Ast.Value.Boolean(false, new SpanData(fileName, iterator.index()));
            default -> {
                if(id.contains(":"))
                    yield new Ast.Value.ResourceLocationValue(new ResourceLocation(id), new SpanData(fileName, iterator.index()));
                else
                    yield new Ast.Value.Literal(id, new SpanData(fileName, iterator.index()));
            }
        };
    }

    public Ast.File parseFile() {
        var commands = new ArrayList<Ast.Command>();
        while (iterator.hasNext())
            commands.add(parseCommand());
        return new Ast.File(
            commands,
            new SpanData(commands.getFirst().span().fileName(), 0)
        );
    }

    public Ast.Command parseCommand() {
        do {
            iterator.skipWhitespace();

            if (iterator.peek() == '#') {
                while (iterator.peek() != '\n')
                    iterator.next();
                iterator.next();
            }

            iterator.skipWhitespace();
        } while (iterator.peek() == '#');


        var name = parseIdentifier();
        var values = new ArrayList<Ast.Value>();
        while (true) {
            iterator.skipSpaces();

            if (iterator.peek() == '\n' || iterator.peek() == '\r' || iterator.peek() == ';')
                break;

            var v = parseValue();

            if (v == null)
                break;

            values.add(v);
        }

        iterator.skipSpaces();

        if (iterator.peek() != '\n' && iterator.peek() != '\r' && iterator.peek() != ';')
            throw new RuntimeException("expected newline or semicolon");

        iterator.next();

        return new Ast.Command(
            name,
            values,
            new SpanData(fileName, iterator.index())
        );
    }

    public Ast.Value parseValue() {
        iterator.skipSpaces();

        if ("-0123456789".contains(String.valueOf(iterator.peek()))) {
            return parseNumber();
        }

        if (iterator.peek() == '"')
            return parseString();

        if (iterator.peek() == '{')
            return parseBlock();

        if (iterator.peek() == '@')
            return parseSelector();

        if (iterator.peek() == '[')
            return parseComponent();

        return parseIdentifierValue();
    }

    public Ast.Value.Selection parseSelector() {
        this.iterator.next();
        var sub = this.iterator.next();
        return new Ast.Value.Selection(
            sub,
            null,
            new SpanData(fileName, iterator.index())
        );
    }

    public Ast.Value.String parseString() {
        var sb = new StringBuilder();
        if (this.iterator.next() != '"') {
            throw new RuntimeException("string needs a \"");
        }
        while (iterator.peek() != '"') {
            if (iterator.peek() == '/') {
                iterator.next();
                iterator.next();
            }
            sb.append(iterator.next());
        }
        if (this.iterator.next() != '"') {
            throw new RuntimeException("string needs a \"");
        }

        return new Ast.Value.String(sb.toString(), new SpanData(fileName, iterator.index()));
    }

    public Ast.Value.Component parseComponent() {
        var map = new HashMap<String, Ast.Value>();

        if (this.iterator.next() != '[') {
            throw new RuntimeException("component needs a [");
        }
        while (iterator.peek() != ']') {
            iterator.skipWhitespace();
            var key = parseIdentifier();

            Logger.debug(iterator.peek());
            if (iterator.next() != '=') {
                throw new RuntimeException("component needs a = " + iterator.index());
            }

            var value = parseValue();

            map.put(key, value);

            iterator.skipWhitespace();
            if (iterator.peek() == ',')
                iterator.next();

            iterator.skipWhitespace();
        }
        if (this.iterator.next() != ']') {
            throw new RuntimeException("component needs a ] " + iterator.index());
        }

        return new Ast.Value.Component(map, new SpanData(fileName, iterator.index()));
    }

    public Ast.Value parseNumber() {
        var sb = new StringBuilder();
        while ("-1234567890.".contains(String.valueOf(iterator.peek()))) {
            sb.append(iterator.next());
        }
        iterator.skipSpaces();
        if(sb.toString().equals("-")) {
            return new Ast.Value.Literal(sb.toString(), new SpanData(fileName, iterator.index()));
        }
        return new Ast.Value.Number(Double.parseDouble(sb.toString()), new SpanData(fileName, iterator.index()));
    }

    public Ast.Value.Block parseBlock() {
        if (this.iterator.next() != '{') {
            throw new RuntimeException("block needs a {");
        }
        var commands = new ArrayList<Ast.Command>();

        iterator.skipWhitespace();
        while (iterator.peek() != '}') {
            iterator.skipWhitespace();
            commands.add(parseCommand());
            iterator.skipWhitespace();
        }
        if (this.iterator.next() != '}') {
            throw new RuntimeException("block needs a }");
        }
        return new Ast.Value.Block(commands, new SpanData(fileName, iterator.index()));
    }
}
