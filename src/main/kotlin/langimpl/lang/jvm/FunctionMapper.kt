package langimpl.lang.jvm

import org.objectweb.asm.Label
import parser.Ast
import parser.Type
import java.sql.SQLIntegrityConstraintViolationException

/**
 * Represents a function mapped to it's JVM blocks.
 * @param blocks A map of labels to it's corresponding block.
 * @param flippedBlocks A flipped map of blocks to it's corresponding labels of the `blocks` parameter`.
 * @param loops A list of blocks that are looping - whether it's `foreach` or `loop`.
 * @param signature The JvmMethodSignature of this function.
 */
data class MappedFunction(
    val blocks: MutableMap<Label, Ast.Block>,
    val flippedBlocks: MutableMap<Ast.Block, Label>,
    val loops: MutableList<Ast.Block>,
    val signature: JvmMethodSignature,
)

/**
 * Converts a provided Ast.Header into a MappedFunction.
 * @see Ast.Header
 * @see MappedFunction
 */
class FunctionMapper {
    private val blocks = mutableMapOf<Label, Ast.Block>()
    private val loops = mutableListOf<Ast.Block>()
    fun map(method: Ast.Header, clazz: Ast.Class): MappedFunction {
        val signature: JvmMethodSignature
        when(method) {
            is Ast.Event -> {
                signature = JvmMethodSignature(
                    "event_${method.name}",
                    clazz.name,
                    listOf(),
                    Type.Void,
                    HeaderType.METHOD
                )
                mapBlock(method.code)
            }
            is Ast.DeclareField -> {
                signature = JvmMethodSignature(
                    method.name,
                    clazz.name,
                    listOf(),
                    method.type,
                    HeaderType.FIELD,
                )
            }
            is Ast.Function -> {
                signature = JvmMethodSignature(
                    method.name.resolve(),
                    clazz.name,
                    method.parameters.toList().map { it.second },
                    method.returns,
                    HeaderType.METHOD
                )
                mapBlock(method.code)
            }
            is Ast.Annotation -> throw SQLIntegrityConstraintViolationException()
        }
        return MappedFunction(
            blocks,
            blocks.map { Pair(it.value, it.key) }.toMap().toMutableMap(),
            loops,
            signature
        )
    }

    private fun mapBlock(block: Ast.Block) {
        val label = Label()
        loops.clear()
        blocks[label] = block

        for(action in block.nodes) {
            mapAction(action)
        }
    }

    fun mapAction(action: Ast.Action) {
        when(action) {
            is Ast.IfStatement -> {
                loops.add(action.ifTrue)
                mapBlock(action.ifTrue)
            }
            is Ast.LoopStatement -> {
                mapBlock(action.block)
            }
            is Ast.ForEachStatement -> {
                mapBlock(action.ifTrue)
            }
            else -> {}
        }
    }
}