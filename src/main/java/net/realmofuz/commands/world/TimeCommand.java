package net.realmofuz.commands.world;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;

public class TimeCommand implements Command {
    @Override
    public String name() {
        return "time";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .literal("set")
                .argument("time", Type.NUMBER)
                .executes(ctx ->
                    ctx.builder().append("time set ")
                        .append(ctx.args().<Ast.Value.Number>get("time").number())
                        .append('\n')
                ),
            ParameterBuilder.of()
                .literal("set")
                .literal("day")
                .executes(ctx -> ctx.builder().append("time set day\n")),
            ParameterBuilder.of()
                .literal("set")
                .literal("midnight")
                .executes(ctx -> ctx.builder().append("time set midnight\n")),
            ParameterBuilder.of()
                .literal("set")
                .literal("night")
                .executes(ctx -> ctx.builder().append("time set night\n")),
            ParameterBuilder.of()
                .literal("set")
                .literal("noon")
                .executes(ctx -> ctx.builder().append("time set noon\n"))
        ).build());
    }
}
