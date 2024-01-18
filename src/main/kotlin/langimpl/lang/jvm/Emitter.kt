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
import java.util.UUID

var className = ""

/**
 * Emits JVM bytecode for the provided Ast.
 * @param gatherer The AstGatherer to reference from.
 */
class Emitter(private val gatherer: AstGatherer) : AstVisitor {
    private lateinit var currentMappedFunction: MappedFunction
    private lateinit var currentClass: Ast.Class
    private lateinit var currentHeader: Ast.Header

    private var classVisitor: ClassVisitor
    private var classWriter: ClassWriter
    private lateinit var methodVisitor: MethodVisitor

    private var localVariables: MutableMap<String, Type> = mutableMapOf()
    private var localVariableIndices: MutableMap<String, Int> = mutableMapOf()
    private var localVariableLabels: MutableMap<String, Label> = mutableMapOf()
    private var localVariableIndex: Int = 0
    private var varHolder: Int = 0

    /*
    Labels that are branched in if/loop/for etc. statements, labels for the main blocks of those
     */
    private val branchLabels: MutableList<Label> = mutableListOf()
    /*
    Labels that will be jumped to after a loop or if statement is completed
     */
    private val continueLabels: MutableList<Label> = mutableListOf()
    /*
    A label that is jumped to after an iteration of a for loop
     */
    private val returnedLabels: MutableList<Label> = mutableListOf()

    private lateinit var endingLabel: Label
    private val classes: MutableList<PathName> = mutableListOf()
    val emittedClasses: MutableMap<String, ByteArray> = mutableMapOf()
    val nativeClasses: MutableList<String> = mutableListOf()

    private val annotations: MutableList<PathName> = mutableListOf()

    init {
        val name = "MainXyraithPlugin_${UUID.randomUUID()}".replace("-", "_")
        className = name
        classVisitor = ClassWriter(2)
        classWriter = classVisitor as ClassWriter

        classWriter.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            name,
            null,
            "org/bukkit/plugin/java/JavaPlugin",
            listOf<String>().toTypedArray()
        )
        val constructor = classWriter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>",
            "()V",
            null,
            null,
        )
        constructor.visitVarInsn(Opcodes.ALOAD, 0)
        constructor.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "org/bukkit/plugin/java/JavaPlugin",
            "<init>",
            "()V",
            false
        )
        constructor.visitInsn(Opcodes.RETURN)
        constructor.visitMaxs(100, 2)
        constructor.visitEnd()

        val onEnable = classWriter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onEnable",
            "()V",
            null,
            null,
        )
        onEnable.visitCode()
        onEnable.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/bukkit/Bukkit",
            "getServer",
            "()Lorg/bukkit/Server;",
            false
        )
        onEnable.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            "org/bukkit/Server",
            "getPluginManager",
            "()Lorg/bukkit/plugin/PluginManager;",
            true
        )

        onEnable.visitTypeInsn(Opcodes.NEW, "events")
        onEnable.visitInsn(Opcodes.DUP)
        onEnable.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "events",
            "<init>",
            "()V",
            false
        )
        onEnable.visitVarInsn(Opcodes.ALOAD, 0)
        onEnable.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            "org/bukkit/plugin/PluginManager",
            "registerEvents",
            "(Lorg/bukkit/event/Listener;Lorg/bukkit/plugin/Plugin;)V",
            true
        )
        onEnable.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "events",
            "event_startup",
            "()V",
            false
        )
        onEnable.visitInsn(Opcodes.RETURN)
        onEnable.visitMaxs(20, 2)
        onEnable.visitEnd()
        classWriter.visitEnd()


        emittedClasses[name] = classWriter.toByteArray()
    }
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
                if(!gatherer.getProperty(altPath.resolve(), fn, value.arguments.map { evaluateType(it.argument) }.toList()).exists) {
                    if(altPath.path.size == 1) {
                        val path2 = value.path.resolve().split(".").toMutableList()
                        val variable = path2[0]
                        val function = path2[1]
                        val signature2 = JvmMethodSignature(
                            function,
                            (evaluateType(Ast.Variable(variable)) as Type.Object).signature,
                            value.arguments.map { evaluateType(it.argument) },
                            value.returns,
                            HeaderType.METHOD,
                        )
                        val property = gatherer.getProperty((evaluateType(Ast.Variable(variable)) as Type.Object).signature.resolve(),
                            function,
                            value.arguments.map { evaluateType(it.argument) })
                        if(!property.exists) {
                            throw InvalidFunction(value.nameSpan)
                        }
                        return gatherer.computeType(
                            (evaluateType(Ast.Variable(variable)) as Type.Object).signature.resolve(),
                            function,
                            value.arguments.map { evaluateType(it.argument) },
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
                val p = gatherer.getProperty(
                    currentClass.name.resolve(),
                    value.value,
                    listOf()
                )
                if(p.exists && p.functionType == FunctionType.MEMBER_FIELD)
                    return p.returnTypeOfProperty
                if(localVariables.containsKey(value.value))
                    return localVariables[value.value]!!
                return Type.Void
            }
        }
    }

    /**
     * Allocates a new local variable and returns the index.
     * @param name Name of the variable
     * @param type Type of the variable
     */
    private fun allocateVariable(
        name: String,
        type: Type,
        startingLabel: Label
    ): Int {
        localVariables[name] = type
        localVariableIndices[name] = localVariableIndex
        // types `long` and `double` take 2 slots, all
        // other types take 1 slot in JVM
        localVariableIndex += if(type == Type.Number) 2 else 1
        localVariableLabels[name] = startingLabel
        return localVariableIndices[name]!!
    }

    override fun visit(clazz: Ast.Class, context: VisitorContext) {
        classes.add(clazz.name)
        currentClass = clazz

        classVisitor = ClassWriter(2)
        classWriter = classVisitor as ClassWriter

        if(!clazz.isNative)
            classVisitor = CheckClassAdapter(classVisitor)
        val pw = PrintWriter(System.out)
        classVisitor = TraceClassVisitor(classVisitor, pw)

        when(clazz.name.resolve()) {
            "events" -> {
                classVisitor.visit(
                    Opcodes.V17,
                    Opcodes.ACC_PUBLIC,
                    clazz.name.resolve().replace(".", "/"),
                    null,
                    clazz.inheritsFrom.signature.resolve().replace(".", "/"),
                    listOf("org/bukkit/event/Listener").toTypedArray()
                )
            }
            else -> {
                classVisitor.visit(
                    Opcodes.V17,
                    Opcodes.ACC_PUBLIC,
                    clazz.name.resolve().replace(".", "/"),
                    null,
                    clazz.inheritsFrom.signature.resolve().replace(".", "/"),
                    null
                )
            }
        }

        classVisitor.visitSource(clazz.span.file, "File: ${clazz.span.file}; Class: ${clazz.name}")


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
    }

    override fun visit(function: Ast.Function, context: VisitorContext): Boolean {
        endingLabel = Label()

        currentHeader = function
        currentMappedFunction = FunctionMapper().map(function, currentClass)

        localVariableIndex = 0
        localVariables.clear()
        localVariableIndices.clear()
        localVariableLabels.clear()

        if(annotations.contains(PathName.parse("native")))
            return false

        val label = Label()

        if(!(currentClass.static || annotations.contains(PathName.parse("static")))) {
            allocateVariable(
                "this",
                Type.Object(
                    currentClass.name,
                    currentClass.generics,
                    false,
                ),
                label
            )
        }


        for(argument in function.parameters) {
            allocateVariable(
                argument.key,
                argument.value,
                label
            )
        }


        if(currentClass.static || annotations.contains(PathName.parse("static"))) {
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
        methodVisitor.visitLabel(label)
        methodVisitor.visitLineNumber(function.eventNameSpan.calculateLineNumber(), label)


        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(event: Ast.Event, context: VisitorContext): Boolean {
        localVariableIndex = 0
        localVariables = mutableMapOf()

        endingLabel = Label()

        if(currentClass.name.resolve() != "events") {
            throw SQLIntegrityConstraintViolationException()
        }


        // https://jd.papermc.io/paper/1.20/org/bukkit/event/player/package-summary.html
        // add more events from there!
        val eventType = when(event.name) {
            "startup" -> "V"
            "player_join" -> "org/bukkit/event/player/PlayerJoinEvent"
            "leave" -> "org/bukkit/event/player/PlayerQuitEvent"
            "interact" -> "org/bukkit/event/player/PlayerInteractEvent"
            "interact_with_entity" -> "org/bukkit/event/player/PlayerInteractEntityEvent"
            "drop_item" -> "org/bukkit/event/player/PlayerDropItemEvent"
            "fish" -> "org/bukkit/event/player/PlayerFishEvent"
            "pickup" -> "org/bukkit/event/player/PlayerPickupItemEvent"
            "command" -> "org/bukkit/event/player/PlayerCommandPreprocessEvent"
            "toggle_sneak" -> "org/bukkit/event/player/PlayerToggleSneakEvent"
            "toggle_sprint" -> "org/bukkit/event/player/PlayerToggleSprintEvent"
            else -> throw InvalidFunction(event.eventNameSpan)
        }
        val label = Label()
        currentHeader = event
        currentMappedFunction = FunctionMapper().map(event, currentClass)

        when(event.name) {
            "startup" -> {
                methodVisitor = classVisitor.visitMethod(
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                    "event_${event.name}",
                    "()V",
                    null,
                    null
                )
            }
            else -> {
                methodVisitor = classVisitor.visitMethod(
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                    "event_join",
                    "(L${eventType};)V",
                    null,
                    null
                )
                allocateVariable(
                    "event",
                    Type.Object(
                        PathName.parse(eventType.replace("/", ".")),
                        listOf(),
                        false
                    ),
                    label
                )
            }
        }
        if(event.name != "startup") {
            methodVisitor.visitAnnotation(
                "Lorg/bukkit/event/EventHandler;",
                true
            )
        }

        methodVisitor.visitCode()
        methodVisitor.visitLabel(label)
        methodVisitor.visitLineNumber(event.eventNameSpan.calculateLineNumber(), label)

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

        if(currentClass.static || annotations.contains(PathName.parse("static"))) {
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
                val ty =
                    if(value.type is Type.Void)
                        evaluateType(value.arguments[0].argument)
                    else
                        value.type
                when(ty) {
                    is Type.Array -> methodVisitor.visitInsn(Opcodes.ANEWARRAY)
                    Type.Boolean -> methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
                    Type.Number -> methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE)
                    is Type.Object -> methodVisitor.visitInsn(Opcodes.ANEWARRAY)
                    Type.JVMFloat-> {
                        methodVisitor.visitInsn(Opcodes.F2D)
                        methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE)
                    }
                    Type.JVMInteger -> {
                        methodVisitor.visitInsn(Opcodes.I2D)
                        methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE)
                    }
                    Type.Void -> TODO()
                    is Type.NumberParameter -> TODO()
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
                val p = gatherer.getProperty(currentClass.name.resolve(), value.value, listOf())
                if(p.exists && p.functionType == FunctionType.MEMBER_FIELD) {
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
                    methodVisitor.visitFieldInsn(
                        Opcodes.GETFIELD,
                        currentClass.name.resolve().replace(".", "/"),
                        value.value,
                        p.returnTypeOfProperty.toJvmSignature()
                    )
                    return
                }
                when(localVariables[value.value]!!) {
                    is Type.Array -> methodVisitor.visitVarInsn(Opcodes.ALOAD, localVariableIndices[value.value]!!)
                    Type.Boolean -> methodVisitor.visitVarInsn(Opcodes.ILOAD, localVariableIndices[value.value]!!)
                    Type.Number -> methodVisitor.visitVarInsn(Opcodes.DLOAD, localVariableIndices[value.value]!!)
                    is Type.Object -> methodVisitor.visitVarInsn(Opcodes.ALOAD, localVariableIndices[value.value]!!)
                    Type.Void -> TODO()
                    Type.JVMFloat -> {
                        methodVisitor.visitVarInsn(Opcodes.FLOAD, localVariableIndices[value.value]!!)
                        methodVisitor.visitInsn(Opcodes.F2D)
                    }
                    Type.JVMInteger -> {
                        methodVisitor.visitVarInsn(Opcodes.ILOAD, localVariableIndices[value.value]!!)
                        methodVisitor.visitInsn(Opcodes.I2D)
                    }

                    is Type.NumberParameter -> TODO()
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
                Type.Void -> TODO()
                Type.JVMFloat -> {
                    methodVisitor.visitInsn(Opcodes.F2D)
                    methodVisitor.visitInsn(Opcodes.DASTORE)
                }
                Type.JVMInteger -> {
                    methodVisitor.visitInsn(Opcodes.I2D)
                    methodVisitor.visitInsn(Opcodes.DASTORE)
                }

                is Type.NumberParameter -> TODO()
            }
            if(!context.isLast)
                methodVisitor.visitInsn(Opcodes.DUP)
        }
    }

    override fun visit(clazz: Ast.ConstructClass, context: VisitorContext) {
        val label = Label()
        methodVisitor.visitLabel(label)
        methodVisitor.visitLineNumber(clazz.classSpan.calculateLineNumber(), label)
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

    fun handleSpecialAstAccess(access: Ast.Access, context: VisitorContext): Boolean {
        val label = Label()
        methodVisitor.visitLabel(label)
        methodVisitor.visitLineNumber(access.nameSpan.calculateLineNumber(), label)
        when(access.path.resolve()) {
            "eq" -> {
                branchLabels.add(Label())
                continueLabels.add(Label())
                // should evaluate to 1 when true, 0 when false
                when(evaluateType(access.arguments[0].argument)) {
                    is Type.Array, is Type.Object -> {
                        methodVisitor.visitJumpInsn(Opcodes.IF_ACMPEQ, branchLabels.last())
                        methodVisitor.visitLdcInsn(1)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(branchLabels.removeLast())
                        methodVisitor.visitLdcInsn(0)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(continueLabels.removeLast())
                    }
                    Type.Boolean -> {
                        methodVisitor.visitJumpInsn(Opcodes.IF_ICMPEQ, branchLabels.last())
                        methodVisitor.visitLdcInsn(1)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(branchLabels.removeLast())
                        methodVisitor.visitLdcInsn(0)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(continueLabels.removeLast())
                    }
                    Type.Number -> {
                        methodVisitor.visitInsn(Opcodes.DCMPL)
                        methodVisitor.visitJumpInsn(Opcodes.IFEQ, branchLabels.last())
                        methodVisitor.visitLdcInsn(1)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(branchLabels.removeLast())
                        methodVisitor.visitLdcInsn(0)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(continueLabels.removeLast())
                    }
                    Type.Void -> TODO()
                    Type.JVMFloat -> {
                        methodVisitor.visitInsn(Opcodes.F2D)
                        methodVisitor.visitInsn(Opcodes.DCMPL)
                        methodVisitor.visitJumpInsn(Opcodes.IFEQ, branchLabels.last())
                        methodVisitor.visitLdcInsn(1)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(branchLabels.removeLast())
                        methodVisitor.visitLdcInsn(0)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(continueLabels.removeLast())
                    }
                    Type.JVMInteger -> {
                        methodVisitor.visitInsn(Opcodes.I2D)
                        methodVisitor.visitInsn(Opcodes.DCMPL)
                        methodVisitor.visitJumpInsn(Opcodes.IFEQ, branchLabels.last())
                        methodVisitor.visitLdcInsn(1)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(branchLabels.removeLast())
                        methodVisitor.visitLdcInsn(0)
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
                        methodVisitor.visitLabel(continueLabels.removeLast())
                    }

                    is Type.NumberParameter -> TODO()
                }
                return false
            }
            "jvmarraylen" -> {
                methodVisitor.visitInsn(Opcodes.ARRAYLENGTH)
                methodVisitor.visitInsn(Opcodes.I2D)
                return false
            }
            "jvmarrayindex" -> {
                methodVisitor.visitInsn(Opcodes.D2I)
                when((evaluateType(access.arguments[0].argument) as Type.Array).type) {
                    is Type.Array -> methodVisitor.visitInsn(Opcodes.AALOAD)
                    Type.Boolean ->  methodVisitor.visitInsn(Opcodes.IALOAD)
                    Type.Number ->  methodVisitor.visitInsn(Opcodes.DALOAD)
                    is Type.Object -> methodVisitor.visitInsn(Opcodes.AALOAD)
                    Type.Void -> TODO()
                    Type.JVMFloat -> {
                        methodVisitor.visitInsn(Opcodes.FALOAD)
                        methodVisitor.visitInsn(Opcodes.F2D)
                    }
                    Type.JVMInteger -> {
                        methodVisitor.visitInsn(Opcodes.IALOAD)
                        methodVisitor.visitInsn(Opcodes.I2D)
                    }

                    is Type.NumberParameter -> TODO()
                }
                return false
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
                return false
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
                    Type.Void -> methodVisitor.visitInsn(Opcodes.RETURN)
                    Type.JVMFloat -> {
                        methodVisitor.visitInsn(Opcodes.F2D)
                        methodVisitor.visitInsn(Opcodes.DRETURN)
                    }
                    Type.JVMInteger -> {
                        methodVisitor.visitInsn(Opcodes.I2D)
                        methodVisitor.visitInsn(Opcodes.DRETURN)
                    }

                    is Type.NumberParameter -> TODO()
                }
                return false
            }
            "d2i" -> { methodVisitor.visitInsn(Opcodes.D2I); return false }
            "d2f" -> { methodVisitor.visitInsn(Opcodes.D2F); return false }
            else -> return true
        }
    }

    override fun visit(access: Ast.Access, context: VisitorContext) {
        when(access.path.resolve()) {
            "eq", "jvmarraylen", "jvmarrayindex",
            "add", "sub", "mul", "div", "return",
            "d2i", "d2f"-> {
                return
            }
        }
        if(access.path.path.size == 2
            && localVariables.containsKey(access.path.path[0])) {
            val variable = access.path.path[0]
            val function = access.path.path[1]
            val altSig = JvmMethodSignature(
                function,
                (evaluateType(Ast.Variable(variable)) as Type.Object).signature,
                access.arguments.map { evaluateType(it.argument) },
                evaluateType(access),
                HeaderType.METHOD
            )

            if((gatherer.getProperty(
                    (evaluateType(Ast.Variable(variable)) as Type.Object).signature.resolve(),
                    function,
                    access.arguments.map { evaluateType(it.argument) }
            ).exists || localVariables.containsKey(access.path.path[0])) &&
                access.path.path.size == 2) {
                visit(Ast.Variable(access.path.path[0]), context)
            }

            return
        }

        val fprop = (gatherer.getProperty(
            currentClass.name.resolve(),
            access.path.path[0],
            listOf()
        ))
        println("fprop: $fprop")
        if(fprop.exists && access.path.path.size == 2) {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                currentClass.name.resolve().replace(".", "/"),
                access.path.path[0],
                (gatherer.getProperty(
                    currentClass.name.resolve(),
                    access.path.path[0],
                    listOf()
                )).returnTypeOfProperty.toJvmSignature()
            )
        }

        return
    }
    override fun visitEnd(access: Ast.Access, context: VisitorContext) {
        if (!handleSpecialAstAccess(access, context))
            return

        val altPath = PathName.parse(access.path.resolve())
        val fn = altPath.path.removeLast()

        // check for field
        val fieldProperty = gatherer.getProperty(
            currentClass.name.resolve(),
            altPath.path[0],
            listOf()
        )
        println("fieldprop ty: ${fieldProperty.functionType}")
        if (altPath.path.size == 1
            && fieldProperty.exists
            && fieldProperty.functionType == FunctionType.MEMBER_FIELD
        ) {
            val finalProperty = gatherer.getProperty(
                (fieldProperty.returnTypeOfProperty as Type.Object).signature.resolve(),
                fn,
                access.arguments.map { evaluateType(it.argument) }
            )
            val sig = JvmMethodSignature(
                fn,
                PathName.parse(finalProperty.resultClass),
                finalProperty.parameterTypesRequested,
                finalProperty.returnTypeOfProperty,
                finalProperty.functionType.toHeaderType()
            )
            when(finalProperty.functionType) {
                FunctionType.MEMBER_METHOD, FunctionType.STATIC_METHOD -> methodVisitor.visitMethodInsn(
                    if(finalProperty.isInterface) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL,
                    sig.ownerSignature(),
                    fn,
                    sig.methodSignature(),
                    false
                )
                FunctionType.MEMBER_FIELD, FunctionType.STATIC_FIELD -> methodVisitor.visitFieldInsn(
                    Opcodes.GETFIELD,
                    sig.ownerSignature(),
                    fn,
                    sig.methodSignature()
                )
                FunctionType.NONE -> TODO()
            }

            return
        }


        // check for variable
        if (altPath.path.size == 1
            && localVariables.containsKey(altPath.path[0])
        ) {

            val variableProperty = gatherer.getProperty(
                (evaluateType(Ast.Variable(altPath.path[0])) as Type.Object).signature.resolve(),
                fn,
                access.arguments.map { evaluateType(it.argument) }
            )
            if (!variableProperty.exists)
                throw InvalidFunction(access.nameSpan)
            if (variableProperty.isInterface) {
                when (variableProperty.functionType) {
                    FunctionType.MEMBER_FIELD -> methodVisitor.visitFieldInsn(
                        Opcodes.GETFIELD,
                        variableProperty.resultClass.replace(".", "/"),
                        fn,
                        variableProperty.returnTypeOfProperty.toJvmSignature()
                    )
                    FunctionType.MEMBER_METHOD -> methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEINTERFACE,
                        variableProperty.resultClass.replace(".", "/"),
                        fn,
                        variableProperty.returnTypeOfProperty.toJvmSignature(),
                        true
                    )
                    else -> TODO()
                }
            } else {
                val sig = JvmMethodSignature(
                    fn,
                    PathName.parse(variableProperty.resultClass),
                    variableProperty.parameterTypesRequested,
                    variableProperty.returnTypeOfProperty,
                    if(variableProperty.functionType == FunctionType.MEMBER_FIELD
                        || variableProperty.functionType == FunctionType.STATIC_FIELD)
                            HeaderType.FIELD
                    else
                            HeaderType.METHOD
                )
                when (variableProperty.functionType) {
                    FunctionType.MEMBER_FIELD -> methodVisitor.visitFieldInsn(
                        Opcodes.GETFIELD,
                        sig.ownerSignature(),
                        fn,
                        sig.methodSignature()
                    )

                    FunctionType.MEMBER_METHOD -> methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        sig.ownerSignature(),
                        fn,
                        sig.methodSignature(),
                        false
                    )
                    else -> TODO()
                }
            }
            return
        }

        // check for static
        val staticProperty = gatherer.getProperty(
            altPath.resolve(),
            fn,
            access.arguments.map { evaluateType(it.argument) }
        )
        if (!staticProperty.exists)
            throw InvalidFunction(access.nameSpan)

        val sig = JvmMethodSignature(
            fn,
            PathName.parse(staticProperty.resultClass),
            staticProperty.parameterTypesRequested,
            staticProperty.returnTypeOfProperty,
            staticProperty.functionType.toHeaderType()
        )
        when (staticProperty.functionType) {
            FunctionType.STATIC_FIELD -> methodVisitor.visitFieldInsn(
                Opcodes.GETSTATIC,
                sig.ownerSignature(),
                fn,
                sig.methodSignature()
            )
            FunctionType.STATIC_METHOD -> methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                sig.ownerSignature(),
                fn,
                sig.methodSignature(),
                false
            )
            else -> TODO()
        }
    }


    override fun visit(declareVariable: Ast.DeclareVariable, context: VisitorContext) {
        val label = Label()
        methodVisitor.visitLabel(label)
        methodVisitor.visitLineNumber(declareVariable.span.calculateLineNumber(), label)

        if(declareVariable.type is Type.Void)
            declareVariable.type = evaluateType(declareVariable.value)

        val index = allocateVariable(
            declareVariable.name,
            declareVariable.type,
            label
        )

        when(declareVariable.type) {
            is Type.Array -> methodVisitor.visitVarInsn(Opcodes.ASTORE, index)
            Type.Boolean -> methodVisitor.visitVarInsn(Opcodes.ISTORE, index)
            Type.Number -> methodVisitor.visitVarInsn(Opcodes.DSTORE, index)
            is Type.Object -> methodVisitor.visitVarInsn(Opcodes.ASTORE, index)
            Type.Void -> methodVisitor.visitVarInsn(Opcodes.ASTORE, index)
            Type.JVMFloat -> {
                methodVisitor.visitInsn(Opcodes.F2D)
                methodVisitor.visitVarInsn(Opcodes.DSTORE, index)
            }
            Type.JVMInteger -> {
                methodVisitor.visitInsn(Opcodes.I2D)
                methodVisitor.visitVarInsn(Opcodes.DSTORE, index)
            }

            is Type.NumberParameter -> TODO()
        }
    }

    override fun visit(storeVariable: Ast.StoreVariable, context: VisitorContext) {
        when(localVariables[storeVariable.name]!!) {
            is Type.Array -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndices[storeVariable.name]!!)
            Type.Boolean -> methodVisitor.visitVarInsn(Opcodes.ISTORE, localVariableIndices[storeVariable.name]!!)
            Type.Number -> methodVisitor.visitVarInsn(Opcodes.DSTORE, localVariableIndices[storeVariable.name]!!)
            is Type.Object -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndices[storeVariable.name]!!)
            Type.Void -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVariableIndices[storeVariable.name]!!)
            Type.JVMFloat -> {
                methodVisitor.visitInsn(Opcodes.F2D)
                methodVisitor.visitVarInsn(Opcodes.DSTORE, localVariableIndices[storeVariable.name]!!)
            }
            Type.JVMInteger -> {
                methodVisitor.visitInsn(Opcodes.I2D)
                methodVisitor.visitVarInsn(Opcodes.DSTORE, localVariableIndices[storeVariable.name]!!)
            }

            is Type.NumberParameter -> TODO()
        }
    }

    override fun visit(ifStatement: Ast.IfStatement, context: VisitorContext) {
        val label = Label()
        methodVisitor.visitLabel(label)
        methodVisitor.visitLineNumber(ifStatement.ifTrue.span.calculateLineNumber(), label)

        branchLabels.add(Label())
        continueLabels.add(Label())
        methodVisitor.visitJumpInsn(Opcodes.IFNE, branchLabels.last())
        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
        methodVisitor.visitLabel(branchLabels.last())
        branchLabels.last()
    }

    override fun visit(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {

        val startingLabel = Label()
        val returnedLabel = Label()
        val loopLabel = Label()
        val continueLabel = Label()

        varHolder++

        val loopValueVarName = "loop_value_${varHolder}"
        val loopValueVarIndex = allocateVariable(
            loopValueVarName,
            Type.Number,
            startingLabel)


        methodVisitor.visitVarInsn(Opcodes.ASTORE, loopValueVarIndex)

        val loopIndexVarName = "loop_index_${varHolder}"
        val loopIndexVarIndex = allocateVariable(
            loopIndexVarName,
            Type.Number,
            startingLabel)
        methodVisitor.visitLdcInsn(-1.0)
        methodVisitor.visitVarInsn(Opcodes.DSTORE, loopIndexVarIndex)

        methodVisitor.visitLabel(startingLabel)

        val list = evaluateType(forEachStatement.list) as Type.Array
        val variableIndex = allocateVariable(forEachStatement.variable, list.type, startingLabel)


        methodVisitor.visitLabel(returnedLabel)

        methodVisitor.visitVarInsn(Opcodes.DLOAD, loopIndexVarIndex)
        methodVisitor.visitLdcInsn(1.0)
        methodVisitor.visitInsn(Opcodes.DADD)
        methodVisitor.visitVarInsn(Opcodes.DSTORE, loopIndexVarIndex)



        methodVisitor.visitVarInsn(Opcodes.DLOAD, loopIndexVarIndex)
        methodVisitor.visitInsn(Opcodes.D2I)
        methodVisitor.visitVarInsn(Opcodes.ALOAD, loopValueVarIndex)
        methodVisitor.visitInsn(Opcodes.ARRAYLENGTH)
        methodVisitor.visitJumpInsn(Opcodes.IF_ICMPEQ, continueLabel)

        methodVisitor.visitVarInsn(Opcodes.ALOAD, loopValueVarIndex)
        methodVisitor.visitVarInsn(Opcodes.DLOAD, loopIndexVarIndex)
        methodVisitor.visitInsn(Opcodes.D2I)
        methodVisitor.visitInsn(Opcodes.DALOAD)
        methodVisitor.visitVarInsn(Opcodes.DSTORE, variableIndex)
        methodVisitor.visitJumpInsn(Opcodes.GOTO, loopLabel)

        continueLabels.add(continueLabel)

        methodVisitor.visitJumpInsn(Opcodes.GOTO, continueLabels.last())
        methodVisitor.visitLabel(loopLabel)

        branchLabels.add(loopLabel)
        returnedLabels.add(returnedLabel)
    }

    override fun visit(loopStatement: Ast.LoopStatement, context: VisitorContext) {
        val label = Label()
        methodVisitor.visitLabel(label)
        methodVisitor.visitLineNumber(loopStatement.block.span.calculateLineNumber(), label)

        val looping = Label()
        val continued = Label()
        continueLabels.add(continued)
        branchLabels.add(looping)
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
        methodVisitor.visitJumpInsn(Opcodes.GOTO, returnedLabels.removeLast())
        methodVisitor.visitLabel(continueLabels.removeLast())
    }

    override fun visitEnd(loopStatement: Ast.LoopStatement, context: VisitorContext) {
        methodVisitor.visitJumpInsn(Opcodes.GOTO, returnedLabels.last())
        methodVisitor.visitLabel(continueLabels.removeLast())
    }

    override fun visitEnd(header: Ast.Header, context: VisitorContext) {
        if(annotations.contains(PathName.parse("native")))
            return

        println(localVariableIndices)
        println(localVariables)
        methodVisitor.visitLabel(endingLabel)
        methodVisitor.visitInsn(Opcodes.RETURN)

        localVariableLabels.forEach {
            methodVisitor.visitLocalVariable(
                it.key,
                localVariables[it.key]!!.toJvmSignature(),
                null,
                it.value,
                endingLabel,
                localVariableIndices[it.key]!!
            )
        }
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