package net.realmofuz.compile.parameters;

import net.realmofuz.commanddata.ParseFailure;
import net.realmofuz.compile.contexts.CompileContext;
import net.realmofuz.compile.contexts.CompileError;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;
import net.realmofuz.util.Result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public sealed interface ParameterNode {
    final class SimpleArgument implements ParameterNode {
        public String name;
        public Type type;
        public ParameterNode child;

        public SimpleArgument(
            String name,
            Type type,
            ParameterNode child
        ) {
            this.name = name;
            this.type = type;
            this.child = child;
        }

        @Override
        public Result<Consumer<CompileContext>, ParseFailure> validateArgument(
            ArgumentIterator iterator,
            String commandName,
            int depth,
            ArgumentSet argumentSet
        ) {
            if (!iterator.hasNext())
                return new Result.Err<>(new ParseFailure(
                    new CompileError.UnexpectedEndOfCommand(
                        this.type,
                        iterator.lastSpan()
                    ),
                    true,
                    depth
                ));
            var next = iterator.next();
            System.out.println(next.type() + " vs. " + this.type);
            System.out.println(next );
            if((next.type() instanceof Type.RestrictedMap
            || next.type() instanceof Type.DynamicComponent)
            && this.type instanceof Type.RestrictedMap rm
            && next instanceof Ast.Value.Component cmp) {
                var attemptCheck = rm.typeData().validateMap(
                    cmp,
                    false
                );
                return switch (attemptCheck) {
                    case Result.Ok<Void, CompileError> ok -> {
                        argumentSet.arguments.put(
                            name,
                            next
                        );
                        yield child.validateArgument(iterator, commandName, depth + 1, argumentSet);
                    }
                    case Result.Err<Void, CompileError> err ->
                        new Result.Err<>(new ParseFailure(err.value(), true, depth));
                };
            }
            if (next.type().ifOtherIsSubtype(this.type)) {
                argumentSet.arguments.put(
                    name,
                    next
                );
                return child.validateArgument(iterator, commandName, depth + 1, argumentSet);
            }
            return new Result.Err<>(new ParseFailure(
                new CompileError.UnexpectedType(
                    this.type,
                    next.type(),
                    iterator.lastSpan()
                ),
                true,
                depth
            ));
        }

        @Override
        public String toString() {
            return "argument " + this.name + " " + this.type + " -> " + this.child.toString();
        }

        @Override
        public void attachChildToEnd(ParameterNode child) {
            if(this.child == null)
                this.child = child;
            else
                this.child.attachChildToEnd(child);
        }
    }

    record RootParameter(
        String commandName,
        List<ParameterNode> child
    ) implements ParameterNode {
        @Override
        public Result<Consumer<CompileContext>, ParseFailure> validateArgument(
            ArgumentIterator iterator,
            String commandName,
            int depth,
            ArgumentSet argumentSet
        ) {
            var errors = new ArrayList<ParseFailure>();
            if (this.commandName.equals(commandName))
                for (var node : child) {
                    Result<Consumer<CompileContext>, ParseFailure> result
                        = node.validateArgument(iterator.copy(), commandName, depth + 1, argumentSet);
                    switch (result) {
                        case Result.Ok<Consumer<CompileContext>, ParseFailure> ok -> {
                            return ok;
                        }
                        case Result.Err<Consumer<CompileContext>, ParseFailure> err -> errors.add(err.value());
                    }
                }

            var errStream = errors
                .stream()
                .max(Comparator.comparingInt(ParseFailure::depth));

            return errStream
                .<Result<Consumer<CompileContext>, ParseFailure>>map(Result.Err::new)
                .orElseGet(() -> new Result.Err<>(new ParseFailure(
                    new CompileError.NotACommand(commandName, iterator.lastSpan()),
                    false,
                    depth
                )));

        }

        @Override
        public String toString() {
            return "root " + this.commandName + " -> " + this.child.toString();
        }

        @Override
        public void attachChildToEnd(ParameterNode child) {
            this.child.add(child);
        }
    }

    final class LiteralKeyword implements ParameterNode {
        public String value;
        public ParameterNode child;

        public LiteralKeyword(
            String value,
            ParameterNode child
        ) {
            this.value = value;
            this.child = child;
        }

        public Result<Consumer<CompileContext>, ParseFailure> validateArgument(
            ArgumentIterator iterator,
            String commandName,
            int depth,
            ArgumentSet argumentSet
        ) {
            if (!iterator.hasNext())
                return new Result.Err<>(new ParseFailure(
                    new CompileError.UnexpectedEndOfCommand(
                        new Type.String(),
                        iterator.lastSpan()
                    ),
                    true,
                    depth
                ));
            var next = iterator.next();
            if (!(next instanceof Ast.Value.Literal avs)) {
                return new Result.Err<>(new ParseFailure(
                    new CompileError.UnexpectedType(new Type.Literal(), next.type(), iterator.lastSpan()),
                    true,
                    depth
                ));
            }
            if (!avs.value().equals(value)) {
                return new Result.Err<>(new ParseFailure(
                    new CompileError.UnexpectedLiteral(value, iterator.lastSpan()),
                    false,
                    depth
                ));
            }
            return child.validateArgument(iterator, commandName, depth + 1, argumentSet);
        }

        @Override
        public String toString() {
            return "literal " + this.value + " -> " + this.child.toString();
        }

        @Override
        public void attachChildToEnd(ParameterNode child) {
            if(this.child == null)
                this.child = child;
            else
                this.child.attachChildToEnd(child);
        }
    }

    record Executes(
        Consumer<CompileContext> contextConsumer
    ) implements ParameterNode {
        @Override
        public Result<Consumer<CompileContext>, ParseFailure> validateArgument(
            ArgumentIterator iterator,
            String commandName,
            int depth,
            ArgumentSet argumentSet
        ) {
            if(iterator.hasNext()) {
                return new Result.Err<>(new ParseFailure(
                    new CompileError.TooManyArguments(
                        depth-1, iterator.length(), iterator.lastSpan()
                    ),
                    false,
                    depth
                ));
            }
            return new Result.Ok<>(this.contextConsumer());
        }

        @Override
        public String toString() {
            return "executes";
        }

        @Override
        public void attachChildToEnd(ParameterNode child) {

        }
    }

    Result<Consumer<CompileContext>, ParseFailure> validateArgument(
        ArgumentIterator iterator,
        String commandName,
        int depth,
        ArgumentSet argumentSet
    );

    void attachChildToEnd(ParameterNode child);
}
