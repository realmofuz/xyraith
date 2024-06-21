package net.realmofuz.compile.parameters;

import net.realmofuz.commanddata.ParseFailure;
import net.realmofuz.compile.contexts.CompileContext;
import net.realmofuz.compile.contexts.CompileSingleCommandResult;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.util.Logger;
import net.realmofuz.util.Result;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public record ParameterSet(
    ParameterNode parameters
) {
    public Result<CompileSingleCommandResult, ParseFailure> tryParseArguments(
        List<Ast.Value> arguments,
        String commandName,
        Ast.Command command
    ) {
        var as = new ArgumentSet(new HashMap<>());
        var r = this.parameters().validateArgument(
            new ArgumentIterator(arguments, command),
            commandName,
            0,
            as
        );
        switch (r) {
            case Result.Ok<Consumer<CompileContext>, ParseFailure> ok -> {
                return new Result.Ok<>(new CompileSingleCommandResult(
                    as,
                    ok.value()
                ));
            }
            case Result.Err<Consumer<CompileContext>, ParseFailure> err -> {
                return new Result.Err<>(err.value());
            }
        }
    }
}
