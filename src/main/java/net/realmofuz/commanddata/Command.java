package net.realmofuz.commanddata;

import net.realmofuz.commands.controlflow.AsCommand;
import net.realmofuz.commands.controlflow.FunctionCommand;
import net.realmofuz.commands.controlflow.IfCommand;
import net.realmofuz.commands.data.GlobalCommand;
import net.realmofuz.commands.definitions.DefineCommand;
import net.realmofuz.commands.entity.DamageCommand;
import net.realmofuz.commands.entity.KillCommand;
import net.realmofuz.commands.entity.TeleportCommand;
import net.realmofuz.commands.world.SayCommand;
import net.realmofuz.commands.world.TimeCommand;
import net.realmofuz.compile.contexts.CompileSingleCommandResult;
import net.realmofuz.compile.contexts.CompileError;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.util.Logger;
import net.realmofuz.util.Result;

import java.util.List;

/**
 * Represents a template for a command in an `.xr` file.
 */
public interface Command {
    List<Command> commandList = List.of(
        new DefineCommand(),
        new SayCommand(),
        new FunctionCommand(),

        new AsCommand(),
        new IfCommand(),

        new GlobalCommand(),

        new KillCommand(),
        new DamageCommand(),
        new TeleportCommand(),

        new TimeCommand()
    );

    ParameterSet typeSet();
    String name();

    /**
     * Attempts to compile an Ast.Command into a regular command to prepare for
     * execution.
     * @param command The command to compile.
     * @return The compiled command
     */
    static CompileSingleCommandResult tryCompile(Ast.Command command) {
        for(var c : commandList) {
            if(!c.name().equals(command.name()))
                continue;

            var r = Command.tryParseFrom(command, c);

            switch (r) {
                case Result.Ok<CompileSingleCommandResult, CompileError> ok  -> {
                    return ok.value();
                }
                case Result.Err<CompileSingleCommandResult, CompileError> err -> {
                    throw (Error) err.value();
                }
            }
        }
        throw new CompileError.NotACommand(command.name(), command.span());
    }

    /**
     * Attempts to parse an Ast.Command
     * @param command The Ast.Command to parse
     * @param compare The regular command to compare it to.
     * @return Ok if it worked, Err if it did not work.
     */
    static Result<CompileSingleCommandResult, CompileError> tryParseFrom(
        Ast.Command command,
        Command compare
    ) {
        var tryArgs = compare.typeSet().tryParseArguments(command.values(), command.name(), command);

        return switch (tryArgs) {
            case Result.Ok<CompileSingleCommandResult, ParseFailure> ok ->
                new Result.Ok<>(ok.value());
            case Result.Err<CompileSingleCommandResult, ParseFailure> err ->
                new Result.Err<>(err.value().error());
        };
    }
}
