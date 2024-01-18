package langimpl.lang.jvm

import langimpl.error.InvalidClass
import langimpl.error.InvalidFunction
import langimpl.error.InvalidType
import langimpl.lang.parser.AstVisitor
import langimpl.lang.parser.VisitorContext
import parser.Ast
import parser.PathName
import parser.Type

/**
 * Type of a property.
 */
enum class FunctionType {
    STATIC_FIELD,
    STATIC_METHOD,
    MEMBER_FIELD,
    MEMBER_METHOD,
    NONE;

    fun toHeaderType(): HeaderType {
        return when(this) {
            STATIC_FIELD, MEMBER_FIELD -> HeaderType.FIELD
            STATIC_METHOD, MEMBER_METHOD -> HeaderType.METHOD
            NONE -> TODO()
        }
    }
}

/**
 * Validates the provided Ast and an AstGatherer.
 * @param gatherer AstGatherer to compare with.
 */
class AstValidator(val gatherer: AstGatherer) : AstVisitor {
    val classes: MutableList<PathName> = mutableListOf()
    val localVariables: MutableMap<String, Type> = mutableMapOf()
    lateinit var currentClass: Ast.Class
    val annotations: MutableList<PathName> = mutableListOf()

    private fun evaluateType(value: Ast.Value): Type {
        return when(value) {
            is Ast.ArrayOf ->
                if(value.type !is Type.Void)
                    Type.Array(value.type, Type.NumberParameter(value.arguments.size))
                else
                    Type.Array(evaluateType(value.arguments[0].argument), Type.NumberParameter(value.arguments.size))
            is Ast.Boolean -> Type.Boolean
            is Ast.ConstructClass -> Type.Object(
                value.className,
                listOf(),
                false
            )
            is Ast.Access -> {
                when(value.path.resolve()) {
                    "add" -> return Type.Number
                    "sub" -> return Type.Number
                    "mul" -> return Type.Number
                    "div" -> return Type.Number
                    "d2i" -> return Type.JVMInteger
                    "d2f" -> return Type.JVMFloat
                    else -> {}
                }
                val altPath = PathName.parse(value.path.resolve())
                val fn = altPath.path.removeLast()
                val funcSig = JvmMethodSignature(
                    fn,
                    altPath,
                    value.arguments.map { evaluateType(it.argument) }.toList(),
                    value.returns,
                    HeaderType.METHOD
                )
                val isig = funcSig.generateInternalSignature()
                val property = gatherer.getProperty(
                    altPath.resolve(),
                    fn,
                    value.arguments.map { evaluateType(it.argument) }.toList()
                )
                if(!property.exists) {
                    if(value.path.path.size == 2) {
                        val variable = value.path.path[0]
                        val function = value.path.path[1]
                        val type = evaluateType(Ast.Variable(variable)) as Type.Object
                        return gatherer.computeType(
                            type.signature.resolve(),
                            function,
                            value.arguments.map { evaluateType(it.argument) }.toList(),
                            value.nameSpan
                        )
                    } else {
                        throw InvalidFunction(value.nameSpan)
                    }
                }
                return gatherer.computeType(altPath.resolve(), fn, value.arguments.map { evaluateType(it.argument) }.toList(), value.nameSpan)
            }
            Ast.Null -> TODO()
            is Ast.Number -> return Type.Number
            is Ast.StringText -> return Type.Object(
                PathName.parse("java.lang.String"),
                listOf(),
                false
            )
            is Ast.Variable -> {
                val prop = gatherer.getProperty(
                    currentClass.name.resolve(),
                    value.value,
                    listOf()
                )
                println("check prop")
                if(prop.exists && prop.functionType == FunctionType.MEMBER_FIELD)
                    return prop.returnTypeOfProperty
                println("var fallback")
                if(localVariables.containsKey(value.value))
                    return localVariables[value.value]!!
                println("void fallback")
                return Type.Void
            }
        }
    }

    override fun visit(clazz: Ast.Class, context: VisitorContext) {
        currentClass = clazz
        localVariables.clear()
        classes.add(clazz.name)
    }

    override fun visit(block: Ast.Block, context: VisitorContext) {

    }

    override fun visit(annotation: Ast.Annotation, context: VisitorContext) {
        annotations.add(annotation.name)
    }

    override fun visit(function: Ast.Function, context: VisitorContext): Boolean {
        val sig = JvmMethodSignature(
            function.name.resolve().replace(".", "__"),
            currentClass.name,
            function.parameters.values.toList(),
            function.returns,
            HeaderType.METHOD
        )
        if(annotations.contains(PathName.parse("static")) || currentClass.static)
            FunctionType.STATIC_METHOD
        else
            FunctionType.MEMBER_METHOD

        for(argument in function.parameters) {
            localVariables[argument.key] = argument.value
        }
        localVariables["this"] = Type.Object(currentClass.name, listOf(), false)

        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(event: Ast.Event, context: VisitorContext): Boolean {
        when(event.name) {
            "startup" -> {}
            "join" -> {
                localVariables["event"] = Type.Object(
                    PathName.parse("org.bukkit.event.player.PlayerJoinEvent"),
                    listOf(),
                    false
                )
            }
            else -> throw InvalidFunction(event.eventNameSpan)
        }

        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(field: Ast.DeclareField, context: VisitorContext): Boolean {
        val sig = JvmMethodSignature(
            field.name,
            currentClass.name,
            listOf(),
            field.type,
            HeaderType.FIELD
        )

        if(!evaluateType(field.value).equalTo(field.type)
            && !annotations.contains(PathName.parse("native"))) {
            throw InvalidType(field.type, evaluateType(field.value), field.span)
        }
        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(value: Ast.Value, context: VisitorContext) {

    }

    override fun visit(clazz: Ast.ConstructClass, context: VisitorContext) {
        if(!classes.contains(clazz.className)
            && !clazz.className.resolve().startsWith("java.")) {
            throw InvalidClass(clazz.classSpan)
        }
    }

    override fun visitEnd(access: Ast.Access, context: VisitorContext) {}

    override fun visit(access: Ast.Access, context: VisitorContext) {
        val reserved = listOf(
            "add", "sub", "mul", "div", "return", "jvmarraylen", "jvmarrayindex",
            "eq", "d2i", "d2f"
        )
        if(reserved.contains(access.path.resolve()))
            return
        val path = access.path.resolve().split(".").toMutableList()
        val fn = path.removeLast()

        // check static:
        println("STATIC PATH: ${path} | FN: ${fn} | A: ${access.arguments.map { evaluateType(it.argument) }}")
        val prop = gatherer.getProperty(
            path.joinToString("."),
            fn,
            access.arguments.map { evaluateType(it.argument) }
        )
        println("Attempting to compare ${access.arguments.map { evaluateType(it.argument) }}")
        println("path: $path | lvars: $localVariables | Types: ${access.arguments.map { evaluateType(it.argument) }}")
        if(!prop.exists) {
            println("chk field prop: ${gatherer.getProperty(
                currentClass.name.resolve(),
                path[0],
                listOf()
            )}")
            if(path.size == 1 &&
                (localVariables.containsKey(path[0])
                        || gatherer.getProperty(
                            currentClass.name.resolve(),
                            path[0],
                            listOf()
                        ).functionType == FunctionType.MEMBER_FIELD)) {
                val path2 = access.path.resolve().split(".").toMutableList()
                val variable = path2[0]
                val function = path2[1]
                val clazz = (evaluateType(Ast.Variable(variable)) as Type.Object).signature
                if(!gatherer.getProperty(clazz.resolve(), function, access.arguments.map { evaluateType(it.argument) }).exists) {
                    throw InvalidFunction(access.nameSpan)
                }
            } else {
                println("${path.size}")
                throw InvalidFunction(access.nameSpan)
            }
        }
    }

    override fun visit(declareVariable: Ast.DeclareVariable, context: VisitorContext) {
        localVariables[declareVariable.name] = if(declareVariable.type is Type.Void)
            evaluateType(declareVariable.value)
        else
            declareVariable.type

        if(!evaluateType(declareVariable.value).equalTo(localVariables[declareVariable.name]!!)
            && declareVariable.type !is Type.Void) {
            throw InvalidType(
                localVariables[declareVariable.name]!!,
                evaluateType(declareVariable.value),
                declareVariable.span)
        }
    }

    override fun visit(storeVariable: Ast.StoreVariable, context: VisitorContext) {
        if(!evaluateType(storeVariable.value).equalTo(localVariables[storeVariable.name]!!)) {
            throw InvalidType(
                localVariables[storeVariable.name]!!,
                evaluateType(storeVariable.value),
                storeVariable.span)
        }
    }

    override fun visit(ifStatement: Ast.IfStatement, context: VisitorContext) {

    }

    override fun visit(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {
        if(!localVariables.containsKey(forEachStatement.variable)) {
            localVariables[forEachStatement.variable] = (evaluateType(forEachStatement.list) as Type.Array).type
        }
    }

    override fun visit(loopStatement: Ast.LoopStatement, context: VisitorContext) {

    }

    override fun visit(breakStatement: Ast.BreakStatement, context: VisitorContext) {

    }

    override fun visitEnd(ifStatement: Ast.IfStatement, context: VisitorContext) {

    }

    override fun visitEnd(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {

    }

    override fun visitEnd(loopStatement: Ast.LoopStatement, context: VisitorContext) {

    }

    override fun visitEnd(header: Ast.Header, context: VisitorContext) {

    }

    override fun visitEnd(clazz: Ast.Class, context: VisitorContext) {

    }

    override fun visitEnd(block: Ast.Block, context: VisitorContext) {

    }
}