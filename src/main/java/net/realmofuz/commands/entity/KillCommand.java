package net.realmofuz.commands.entity;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;

public class KillCommand implements Command {
    @Override
    public String name() {
        return "kill";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .executes(ctx -> ctx.builder().append("kill @s\n"))
        ).build());
    }
}
