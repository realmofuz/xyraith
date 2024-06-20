package net.realmofuz.compile.parameters;

import net.realmofuz.compile.contexts.CompileContext;
import net.realmofuz.parser.tree.Type;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Lets you build a set of parameters for a command.
 */
public class ParameterBuilder {
    ParameterNode parameterType;

    /**
     * Construct a new Parameter Builder.
     * @return A new Parameter Builder.
     */
    public static ParameterBuilder of() {
        return new ParameterBuilder();
    }

    /**
     * A node representing a name of the command.
     * @param name The command name.
     * @param nodes Child nodes to parse.
     * @return The new builder
     */
    public ParameterBuilder name(
        String name,
        ParameterBuilder... nodes
    ) {
        this.parameterType = new ParameterNode.RootParameter(
            name,
            Arrays.stream(nodes).map(ParameterBuilder::build).toList()
        );
        return this;
    }

    /**
     * Appends an argument node.
     * @param name The name of the argument
     * @param type The type of the argument
     * @return The new ParameterBuilder
     */
    public ParameterBuilder argument(
        String name,
        Type type
    ) {
        var child = new ParameterNode.SimpleArgument(
            name,
            type,
            null
        );

        if(this.parameterType == null)
            this.parameterType = child;
        else
            this.parameterType.attachChildToEnd(child);

        return this;
    }

    /**
     * Appends a literal argument.
     * @param value The literal expected
     * @return The new Parameter Builder
     */
    public ParameterBuilder literal(
        String value
    ) {
        var child = new ParameterNode.LiteralKeyword(
            value,
            null
        );
        if(this.parameterType == null)
            this.parameterType = child;
        else
            this.parameterType.attachChildToEnd(child);
        return this;
    }

    /**
     * Always a terminating node. Represents transformation of a command to
     * the datapack.
     * @param contextConsumer The transformations to perform.
     * @return The new Parameter Builder
     */
    public ParameterBuilder executes(
        Consumer<CompileContext> contextConsumer
    ) {
        var child = new ParameterNode.Executes(
            contextConsumer
        );
        if(this.parameterType == null)
            this.parameterType = child;
        else
            this.parameterType.attachChildToEnd(child);
        return this;
    }

    /**
     * Builds this into a fully-fledged Parameter Node
     * @return The Parameter Type constructed.
     */
    public ParameterNode build() {
        return this.parameterType;
    }
}
