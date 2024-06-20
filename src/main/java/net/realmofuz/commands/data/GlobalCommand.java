package net.realmofuz.commands.data;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.contexts.CompileContext;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;

public class GlobalCommand implements Command {
    @Override
    public String name() {
        return "global";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .argument("variable", Type.LITERAL)
                .literal("=")
                .argument("value", Type.NUMBER)
                .executes(ctx -> modifyGlobalByConstant(ctx, "set")),
            ParameterBuilder.of()
                .argument("variable", Type.LITERAL)
                .literal("+=")
                .argument("value", Type.NUMBER)
                .executes(ctx -> modifyGlobalByConstant(ctx, "add")),
            ParameterBuilder.of()
                .argument("variable", Type.LITERAL)
                .literal("-")
                .literal("=")
                .argument("value", Type.NUMBER)
                .executes(ctx -> modifyGlobalByConstant(ctx, "remove"))
        ).build());
    }

    public static void modifyGlobalByConstant(CompileContext ctx, String mcOp) {
        ctx.builder().append("scoreboard players ")
            .append(mcOp)
            .append(" ")
            .append(ctx.args().<Ast.Value.Literal>get("variable").value())
            .append(" globals ")
            .append(ctx.args().<Ast.Value.Number>get("value").number())
            .append('\n');
    }

    public static void modifyGlobalByOtherScore(
        CompileContext ctx,
        String mcOp,
        String otherScoreObjective,
        String otherScorePlayer
    ) {
        ctx.builder().append("scoreboard players operation ")
            .append(" ")
            .append(ctx.args().<Ast.Value.Literal>get("variable").value())
            .append(" globals ")
            .append(mcOp)
            .append(" ")
            .append(otherScoreObjective)
            .append(" ")
            .append(otherScorePlayer)
            .append('\n');
    }
}
