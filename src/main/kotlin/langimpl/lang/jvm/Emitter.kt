package langimpl.lang.jvm

import langimpl.error.InvalidClass
import langimpl.error.InvalidFunction
import langimpl.error.InvalidType
import langimpl.error.Unreachable
import langimpl.lang.parser.AstVisitor
import langimpl.lang.parser.VisitorContext
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.CheckClassAdapter
import org.objectweb.asm.util.TraceClassVisitor
import parser.Ast
import parser.PathName
import parser.Type
import java.io.PrintWriter
import java.sql.SQLIntegrityConstraintViolationException

class Emitter(private val functionTypes: Map<String, FunctionType>, private val functions: Map<String, Type>) : AstVisitor {
    private lateinit var currentMappedFunction: MappedFunction
    private lateinit var currentClass: Ast.Class
    private lateinit var currentHeader: Ast.Header

    private lateinit var classVisitor: ClassVisitor
    private lateinit var classWriter: ClassWriter
    private lateinit var methodVisitor: MethodVisitor

    private var localVariables: MutableMap<String, Type> = mutableMapOf()
    private var localVariableIndices: MutableMap<String, Int> = mutableMapOf()
    private var localVariableIndex: Int = 0

    private val startLabels: MutableList<Label> = mutableListOf()
    private val continueLabels: MutableList<Label> = mutableListOf()

    private val classes: MutableList<PathName> = mutableListOf()
    val emittedClasses: MutableMap<String, ByteArray> = mutableMapOf()
    val nativeClasses: MutableList<String> = mutableListOf()

    private val annotations: MutableList<PathName> = mutableListOf()

    private fun evaluateType(value: Ast.Value): Type {
        return when(value) {
            is Ast.ArrayOf -> Type.Array(value.type)
            is Ast.Boolean -> Type.Boolean
            is Ast.ConstructClass -> Type.Object(
                value.className,
                listOf(),
                false
            )
            is Ast.Access -> {
                val funcSig = JvmMethodSignature(
                    value.path.resolve().replace(".", "__"),
                    currentClass.name,
                    value.arguments.map { evaluateType(it.argument) }.toList(),
                    value.returns,
                    HeaderType.METHOD
                )
                val isig = funcSig.generateInternalSignature()
                if(!functions.containsKey(isig)) {
                    throw InvalidFunction(value.nameSpan)
                }
                return functions[isig]!!
            }
            Ast.Null -> TODO()
            is Ast.Number -> return Type.Number
            is Ast.StringText -> return Type.String
            is Ast.Variable -> {
                if(localVariables.containsKey(value.value))
                    return localVariables[value.value]!!
                return Type.Void
            }
        }
    }


    override fun visit(clazz: Ast.Class, context: VisitorContext) {
        classes.add(clazz.name)
        currentClass = clazz

        classVisitor = ClassWriter(2)
        classWriter = classVisitor as ClassWriter

        classVisitor = CheckClassAdapter(classVisitor)
        val pw = PrintWriter(System.out)
        classVisitor = TraceClassVisitor(classVisitor, pw)
        classVisitor = TraceClassVisitor(classVisitor, pw)
        classVisitor.visit(
            Opcodes.V1_5,
            Opcodes.ACC_PUBLIC,
            clazz.name.resolve().replace(".", "/"),
            null,
            clazz.inheritsFrom.signature.resolve().replace(".", "/"),
            null
        )

        if(clazz.isNative) {
            return
        }

        methodVisitor = classVisitor.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>",
            "()V",
            null,
            null,
        )

        methodVisitor.visitCode()

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
        methodVisitor.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false
        )

        for(header in clazz.headers) {
            if(header is Ast.DeclareField) {
                val signature = JvmMethodSignature(
                    header.name,
                    clazz.name,
                    listOf(),
                    header.type,
                    HeaderType.FIELD
                )
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
                visit(header.value, context)
                methodVisitor.visitFieldInsn(
                    Opcodes.PUTFIELD,
                    signature.ownerSignature(),
                    header.name,
                    signature.methodSignature()
                )
            }
        }

        methodVisitor.visitInsn(Opcodes.RETURN)
        methodVisitor.visitMaxs(10, 10)
        methodVisitor.visitEnd()

    }

    override fun visit(block: Ast.Block, context: VisitorContext) {
        val label = currentMappedFunction.flippedBlocks[block]
        methodVisitor.visitLabel(label)
    }

    override fun visit(annotation: Ast.Annotation, context: VisitorContext) {
        annotations.add(annotation.name)
        // aaa
    }

    override fun visit(function: Ast.Function, context: VisitorContext): Boolean {
        localVariableIndex = 0
        localVariables = mutableMapOf()

        if(!currentClass.static && !annotations.contains(PathName.parse("jvmstatic"))) {
            localVariables["this"] = Type.Object(
                currentClass.name,
                currentClass.generics,
                false
            )
            localVariableIndices["this"] = localVariableIndex
            localVariableIndex += 1
        }

        for(argument in function.parameters) {
            localVariables[argument.key] = argument.value
            localVariableIndices[argument.key] = localVariableIndex
            localVariableIndex += if(argument.value == Type.Number) 2 else 1
        }
        currentHeader = function
        currentMappedFunction = FunctionMapper().map(function, currentClass)

        if(currentClass.static && !annotations.contains(PathName.parse("jvmstatic"))) {
            methodVisitor = classVisitor.visitMethod(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                function.name.resolve().replace(".", "__"),
                currentMappedFunction.signature.methodSignature(),
                null,
                null
            )
        } else {
            methodVisitor = classVisitor.visitMethod(
                Opcodes.ACC_PUBLIC,
                function.name.resolve().replace(".", "__"),
                currentMappedFunction.signature.methodSignature(),
                null,
                null
            )
        }
        methodVisitor.visitCode()

        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(event: Ast.Event, context: VisitorContext): Boolean {
        localVariableIndex = 0
        localVariables = mutableMapOf()

        if(currentClass.name.resolve() != "ServerEvents") {
            throw SQLIntegrityConstraintViolationException()
        }

        currentHeader = event
        currentMappedFunction = FunctionMapper().map(event, currentClass)

        methodVisitor = classVisitor.visitMethod(
            Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
            "event_${event.name}",
            "()V",
            null,
            null
        )
        methodVisitor.visitCode()

        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(field: Ast.DeclareField, context: VisitorContext): Boolean {
        localVariableIndex = 0
        localVariables = mutableMapOf()

        val fieldSig = JvmMethodSignature(
            field.name,
            currentClass.name,
            listOf(),
            field.type,
            HeaderType.FIELD
        )
        currentHeader = field
        currentMappedFunction = FunctionMapper().map(field, currentClass)

        if(currentClass.static && !annotations.contains(PathName.parse("jvmstatic"))) {
            classVisitor.visitField(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                field.name,
                currentMappedFunction.signature.methodSignature(),
                null,
                null
            )
            val b = !annotations.contains(PathName.parse("native"))
            annotations.clear()
            return b
        }
        classVisitor.visitField(
            Opcodes.ACC_PUBLIC,
            field.name,
            currentMappedFunction.signature.methodSignature(),
            null,
            null
        )
        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(value: Ast.Value, context: VisitorContext) {
        if(context is VisitorContext.Array) {
            methodVisitor.visitLdcInsn(context.index)
        }
        when(value) {
            is Ast.ArrayOf -> {
                methodVisitor.visitLdcInsn(value.arguments.size)
                when(value.type) {
                    is Type.Array -> methodVisitor.visitInsn(Opcodes.ANEWARRAY)
                    Type.Boolean -> methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
                    Type.Number -> methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE)
                    is Type.Object -> methodVisitor.visitInsn(Opcodes.ANEWARRAY)
                    Type.String -> methodVisitor.visitInsn(Opcodes.ANEWARRAY)
                    Type.Void -> TODO()
                }
                methodVisitor.visitInsn(Opcodes.DUP)
            }
            is Ast.Boolean -> {
                if(value.value)
                    methodVisitor.visitLdcInsn(1)
                else
                    methodVisitor.visitLdcInsn(0)
            }
            is Ast.ConstructClass -> {
                visit(value, context)
            }
            Ast.Null -> {
                methodVisitor.visitInsn(Opcodes.NULL)
            }
            is Ast.Number -> {
                methodVisitor.visitLdcInsn(value.value)
            }
            is Ast.Access -> {
                visit(value, context)
            }
            is Ast.StringText -> {
                methodVisitor.visitLdcInsn(value.value)
            }
            is Ast.Variable -> {
                println("value.value: ${value.value} ${localVariableIndices[value.value]} ${localVariables[value.value]}")
                when(localVariables[value.value]!!) {
                    is Type.Array -> methodVisitor.visitVarInsn(Opcodes.ALOAD, localVariableIndices[value.value]!!)
                    Type.Boolean -> methodVisitor.visitVarInsn(Opcodes.ILOAD, localVariableIndices[value.value]!!)
                    Type.Number -> methodVisitor.visitVarInsn(Opcodes.DLOAD, localVariableIndices[value.value]!!)
                    is Type.Object -> methodVisitor.visitVarInsn(Opcodes.ALOAD, localVariableIndices[value.value]!!)
                    Type.String -> methodVisitor.visitVarInsn(Opcodes.ALOAD, localVariableIndices[value.value]!!)
                    Type.Void -> TODO()
                }
            }
        }
        if(context is VisitorContext.Array) {
            val type = evaluateType(value)
            when(type) {
                is Type.Array -> methodVisitor.visitInsn(Opcodes.AASTORE)
                Type.Boolean -> methodVisitor.visitInsn(Opcodes.IASTORE)
                Type.Number -> methodVisitor.visitInsn(Opcodes.DASTORE)
                is Type.Object -> methodVisitor.visitInsn(Opcodes.AASTORE)
                Type.String -> methodVisitor.visitInsn(Opcodes.AASTORE)
                Type.Void -> TODO()
            }
            if(!context.isLast)
                methodVisitor.visitInsn(Opcodes.DUP)
        }
    }

    override fun visit(clazz: Ast.ConstructClass, context: VisitorContext) {
        if(!clazz.className.resolve().startsWith("java.")
            && !classes.contains(clazz.className)) {
            throw InvalidClass(clazz.classSpan)
        }
        methodVisitor.visitTypeInsn(Opcodes.NEW, clazz.className.resolve().replace(".", "/"))
        methodVisitor.visitInsn(Opcodes.DUP)
        methodVisitor.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            clazz.className.resolve().replace(".", "/"),
            "<init>",
            "(${clazz.arguments.map { evaluateType(it.argument).toJvmSignature() }.joinToString { it }})V",
            false
        )
    }

    override fun visit(access: Ast.Access, context: VisitorContext) {
        println("name: ${access.path.resolve()}")
        when(access.path.resolve()) {
            "jvmarraylen" -> {
                methodVisitor.visitInsn(Opcodes.ARRAYLENGTH)
                methodVisitor.visitInsn(Opcodes.I2D)
                return
            }
            "jvmarrayindex" -> {
                methodVisitor.visitInsn(Opcodes.D2I)
                when((evaluateType(access.arguments[0].argument) as Type.Array).type) {
                    is Type.Array -> methodVisitor.visitInsn(Opcodes.AALOAD)
                    Type.Boolean ->  methodVisitor.visitInsn(Opcodes.IALOAD)
                    Type.Number ->  methodVisitor.visitInsn(Opcodes.DALOAD)
                    is Type.Object -> methodVisitor.visitInsn(Opcodes.AALOAD)
                    Type.String -> methodVisitor.visitInsn(Opcodes.AALOAD)
                    Type.Void -> TODO()
                }
                return
            }
            "add", "sub", "mul", "div" -> {
                for(argument in access.arguments) {
                    val type = evaluateType(argument.argument)
                    if(type !is Type.Number)
                        throw InvalidType(Type.Number, evaluateType(argument.argument), argument.span)
                }

                when(access.path.resolve()) {
                    "add" -> methodVisitor.visitInsn(Opcodes.DADD)
                    "sub" -> methodVisitor.visitInsn(Opcodes.DSUB)
                    "mul" -> methodVisitor.visitInsn(Opcodes.DMUL)
                    "div" -> methodVisitor.visitInsn(Opcodes.DDIV)
                }
                return
            }

            "return" -> {
                val type = evaluateType(access.arguments.getOrNull(0)?.argument ?: Ast.Null)
                when(currentHeader) {
                    is Ast.Annotation -> throw Unreachable()
                    is Ast.DeclareField -> throw Unreachable()
                    is Ast.Event -> if(type !is Type.Void) throw InvalidType(Type.Void, type, access.arguments[0].span)
                    is Ast.Function -> if(type != (currentHeader as Ast.Function).returns) throw InvalidType((currentHeader as Ast.Function).returns, type, access.arguments[0].span)
                }
                when(type) {
                    is Type.Array -> methodVisitor.visitInsn(Opcodes.ARETURN)
                    Type.Boolean -> methodVisitor.visitInsn(Opcodes.IRETURN)
                    Type.Number -> methodVisitor.visitInsn(Opcodes.DRETURN)
                    is Type.Object -> methodVisitor.visitInsn(Opcodes.ARETURN)
                    Type.String -> methodVisitor.visitInsn(Opcodes.ARETURN)
                    Type.Void -> methodVisitor.visitInsn(Opcodes.RETURN)
                }
                return
            }
            else -> {}
        }

        val altPath = PathName.parse(access.path.resolve())
        val fn = altPath.path.removeLast()

        val tmpSig = JvmMethodSignature(
            fn,
            altPath,
            access.arguments.map { evaluateType(it.argument) },
            evaluateType(access),
            HeaderType.METHOD
        )

        if(functions.containsKey(tmpSig.generateInternalSignature())) {
            access.returns = functions[tmpSig.generateInternalSignature()]!!
        } else if(!access.path.resolve().startsWith("java.")
            && !access.path.resolve().startsWith("StdBuiltins")) {
            throw InvalidFunction(access.nameSpan)
        }

        val methodSignature = JvmMethodSignature(
            fn,
            altPath,
            access.arguments.map { evaluateType(it.argument) },
            evaluateType(access),
            HeaderType.METHOD
        )
        val fieldSignature = JvmMethodSignature(
            fn,
            altPath,
            access.arguments.map { evaluateType(it.argument) },
            evaluateType(access),
            HeaderType.METHOD
        )

        when(functionTypes[tmpSig.generateInternalSignature()]!!) {
            FunctionType.STATIC_METHOD -> {
                methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    methodSignature.ownerSignature(),
                    fn,
                    methodSignature.methodSignature(),
                    false
                )
            }
            FunctionType.STATIC_FIELD -> {
                methodVisitor.visitFieldInsn(
                    Opcodes.GETSTATIC,
                    methodSignature.ownerSignature(),
                    fn,
                    methodSignature.methodSignature()
                )
            }
            FunctionType.MEMBER_METHOD -> {

            }
            FunctionType.MEMBER_FIELD -> {

            }
        }
    }


    override fun visit(declareVariable: Ast.DeclareVariable, context: VisitorContext) {
        localVariableIndex += 2
        localVariableIndices[declareVariable.name] = localVariableIndex

        if(declareVariable.type is Type.Void)
            declareVariable.type = evaluateType(declareVariable.value)
        localVariables[declareVariable.name] = declareVariable.type
        when(declareVariable.type) {
            is Type.Array -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndex)
            Type.Boolean -> methodVisitor.visitVarInsn(Opcodes.ISTORE, localVariableIndex)
            Type.Number -> methodVisitor.visitVarInsn(Opcodes.DSTORE, localVariableIndex)
            is Type.Object -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndex)
            Type.String -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndex)
            Type.Void -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndex)
        }
    }

    override fun visit(storeVariable: Ast.StoreVariable, context: VisitorContext) {
        when(localVariables[storeVariable.name]!!) {
            is Type.Array -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndices[storeVariable.name]!!)
            Type.Boolean -> methodVisitor.visitVarInsn(Opcodes.ISTORE, localVariableIndices[storeVariable.name]!!)
            Type.Number -> methodVisitor.visitVarInsn(Opcodes.DSTORE, localVariableIndices[storeVariable.name]!!)
            is Type.Object -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndices[storeVariable.name]!!)
            Type.String -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndices[storeVariable.name]!!)
            Type.Void -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndices[storeVariable.name]!!)
        }
    }

    override fun visit(ifStatement: Ast.IfStatement, context: VisitorContext) {
        startLabels.add(Label())
        continueLabels.add(Label())
        methodVisitor.visitJumpInsn(Opcodes.IFNE, startLabels.last())
        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
        methodVisitor.visitLabel(startLabels.last())
        startLabels.last()
    }

    override fun visit(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {

    }

    override fun visit(loopStatement: Ast.LoopStatement, context: VisitorContext) {
        val looping = Label()
        val continued = Label()
        continueLabels.add(continued)
        startLabels.add(looping)
        methodVisitor.visitJumpInsn(Opcodes.GOTO, looping)
        methodVisitor.visitLabel(looping)
    }

    override fun visit(breakStatement: Ast.BreakStatement, context: VisitorContext) {
        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
    }

    override fun visitEnd(ifStatement: Ast.IfStatement, context: VisitorContext) {
        methodVisitor.visitLabel(continueLabels.removeLast())
    }

    override fun visitEnd(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {

    }

    override fun visitEnd(loopStatement: Ast.LoopStatement, context: VisitorContext) {
        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
        methodVisitor.visitLabel(continueLabels.removeLast())
    }

    override fun visitEnd(header: Ast.Header, context: VisitorContext) {
        methodVisitor.visitInsn(Opcodes.RETURN)
        methodVisitor.visitMaxs(100, 100)
        methodVisitor.visitEnd()

        annotations.clear()
    }

    override fun visitEnd(clazz: Ast.Class, context: VisitorContext) {
        classVisitor.visitEnd()
        emittedClasses[clazz.name.resolve().replace(".", "/")] = classWriter.toByteArray()
        if(clazz.isNative)
            nativeClasses.add(clazz.name.resolve().replace(".", "/"))
    }

    override fun visitEnd(block: Ast.Block, context: VisitorContext) {

    }
}