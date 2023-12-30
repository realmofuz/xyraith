package langimpl.lang.legacy
//
//import langimpl.error.UnknownType
//import langimpl.error.Unreachable
//import org.objectweb.asm.*
//import org.objectweb.asm.util.CheckClassAdapter
//import org.objectweb.asm.util.TraceClassVisitor
//import parser.Ast
//import parser.PathName
//import parser.Type
//import java.io.PrintWriter
//import java.io.StringWriter
//import java.sql.SQLIntegrityConstraintViolationException
//
//class LegacyEmitter(private val ast: Ast.Program) {
//    private val functionSignatures: MutableMap<PathName, String> = mutableMapOf()
//    private val functionReturnTypes: MutableMap<PathName, Type> = mutableMapOf()
//    private val localVariableTypes: MutableMap<String, Type> = mutableMapOf()
//    private val localVariableIndices: MutableMap<String, Int> = mutableMapOf()
//    private val globalVariables: MutableMap<String, Type> = mutableMapOf()
//    private val blocks: MutableMap<Label, Ast.Block> = mutableMapOf()
//    private val labels: MutableMap<Int, Label> = mutableMapOf()
//    private val globalClasses: MutableMap<String, Ast.Class> = mutableMapOf()
//
//    private var localVariableIndex = 1
//    private var labelIndex = 0
//
//    private lateinit var currentClass: Ast.Class
//    private lateinit var currentHeader: Ast.Header
//    private lateinit var mappedFunction: MappedFunction
//    private lateinit var currentLoopSurrounding: Label
//
//    fun evaluateType(value: Ast.Value): Type {
//        println("Calculating return type of ${value}")
//        return when(value) {
//            is Ast.ArrayOf -> Type.Array((value.type as Type.Array).type)
//            is Ast.Boolean -> Type.Boolean
//            is Ast.ConstructClass -> Type.Object(
//                value.className,
//                globalClasses[value.className.resolve()]!!.generics,
//                false
//            )
//            is Ast.FunctionCall -> {
//                when(value.name.resolve()) {
//                    "add" -> evaluateType(value.arguments[0].argument)
//                    "sub" -> Type.Number
//                    "mul" -> Type.Number
//                    "div" -> Type.Number
//                    else -> {
//                        if(!functionReturnTypes.containsKey(value.name)) {
//                            throw UnknownType(value.nameSpan)
//                        }
//                        functionReturnTypes[value.name]!!
//                    }
//                }
//
//            }
//            // please don't ask what the heck this is
//            is Ast.MemberAccess -> {
//                println("access: $value")
//                println("path: ${PathName.parse((evaluateType(Ast.Variable(value.variableName)) as Type.Object)
//                    .signature.resolve() + "." + value.name.resolve())}")
//                functionReturnTypes[
//                    PathName.parse((evaluateType(Ast.Variable(value.variableName)) as Type.Object)
//                        .signature.resolve() + "." + value.name.resolve())]!!
//            }
//            Ast.Null -> ()
//            is Ast.Number -> Type.Number
//            is Ast.StringText -> Type.String
//            is Ast.Variable -> {
//                for(header in currentClass.headers) {
//                    if(header is Ast.DeclareField && header.name == value.value) {
//                        return header.type
//                    }
//                }
//                if(globalVariables.containsKey(value.value))
//                    return globalVariables[value.value]!!
//                return localVariableTypes[value.value]!!
//            }
//        }
//    }
//    fun emit(): Map<String, ByteArray> {
//        val classes = mutableMapOf<String, ByteArray>()
//        for(clazz in ast.events) {
//            currentClass = clazz
//            globalClasses[currentClass.name.resolve()] = currentClass
//
//            globalVariables.clear()
//
//            val classWriter = ClassWriter(2)
//            val stringWriter = StringWriter()
//            val printWriter = PrintWriter(stringWriter)
//
//            val checkClass = CheckClassAdapter(classWriter)
//            val traceVisitor = TraceClassVisitor(checkClass, printWriter)
//
//            traceVisitor.visit(Opcodes.V1_5,
//                Opcodes.ACC_PUBLIC,
//                clazz.name.resolve().replace(".", "__"),
//                null,
//                "java/lang/Object",
//                listOf<String>().toTypedArray()
//            )
//            for(header in clazz.headers) {
//                currentHeader = header
//                localVariableIndex = 0
//                labelIndex = 0
//                localVariableTypes.clear()
//                localVariableIndices.clear()
//
//                if(!clazz.static) {
//                    localVariableIndices["this"] = 0
//                    localVariableTypes["this"] = Type.Object(
//                        clazz.name,
//                        clazz.generics,
//                    )
//                }
//
//                emitHeaderInClass(header, traceVisitor)
//            }
//
//            if(!clazz.static) {
//                val method = traceVisitor.visitMethod(
//                    Opcodes.ACC_PUBLIC,
//                    "<init>",
//                    "()V",
//                    null,
//                    listOf<String>().toTypedArray(),
//                )
//                method.visitCode()
//                method.visitVarInsn(Opcodes.ALOAD, 0)
//                method.visitMethodInsn(
//                    Opcodes.INVOKESPECIAL,
//                    "java/lang/Object",
//                    "<init>",
//                    "()V",
//                    false
//                )
//
//                for(header in clazz.headers) {
//                    if(header is Ast.DeclareField) {
//                        method.visitVarInsn(Opcodes.ALOAD, 0)
//                        visitValue(header.value, method)
//                        method.visitFieldInsn(
//                            Opcodes.PUTFIELD,
//                            clazz.name.resolve().replace(".", "/"),
//                            header.name,
//                            header.type.toJvmSignature(),
//                        )
//                    }
//                }
//                method.visitInsn(Opcodes.RETURN)
//                method.visitMaxs(10, 100)
//                method.visitEnd()
//            }
//
//            traceVisitor.visitEnd()
//            println(stringWriter.buffer)
//            classes[clazz.name.resolve().replace(".", "__")] = classWriter.toByteArray()
//        }
//        return classes
//    }
//    private fun emitHeaderInClass(header: Ast.Header, cw: ClassVisitor) {
//        when(header) {
//            is Ast.Event -> {
//                visitEvent(header, cw)
//            }
//            is Ast.Function -> {
//                visitMethod(header, cw)
//            }
//            is Ast.DeclareField -> {
//                globalVariables[header.name] = header.type
//                val access = if(currentClass.static) Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC else Opcodes.ACC_PUBLIC
//                cw.visitField(
//                    access,
//                    header.name,
//                    header.type.toJvmSignature(),
//                    null,
//                    when(header.value) {
//                        is Ast.Number -> header.value.value
//                        is Ast.Boolean -> header.value.value
//                        is Ast.StringText -> header.value.value
//                        else -> null
//                    }
//                )
//            }
//        }
//    }
//
//    fun visitMethod(header: Ast.Function, cw: ClassVisitor) {
//        if(!currentClass.static)
//            localVariableIndex++
//        if(currentClass.name == PathName.parse("ServerEvents")) {
//            throw SQLIntegrityConstraintViolationException()
//        }
//        val joined = PathName.parse(currentClass.name.resolve() + "." + header.name.resolve())
//        functionSignatures[joined] = header.generateSignature()
//        functionReturnTypes[joined] = header.returns
//        val access = if(currentClass.static) Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC else Opcodes.ACC_PUBLIC
//        val name = when(header.name.resolve().replace(".", "__")) {
//            "init" -> "<init>"
//            else -> header.name.resolve().replace(".", "__")
//        }
//        val mv = cw.visitMethod(
//            access,
//            name,
//            header.generateSignature(),
//            null,
//            null
//        )
//
//        for(argument in header.parameters) {
//            localVariableTypes[argument.key] = argument.value
//            localVariableIndices[argument.key] = localVariableIndex
//            localVariableIndex += when(argument.value) {
//                Type.Number -> 2
//                else -> 1
//            }
//        }
//        mv.visitCode()
//
//        if(name == "<init>") {
//            mv.visitVarInsn(Opcodes.ALOAD, 0)
//            mv.visitMethodInsn(
//                Opcodes.INVOKESPECIAL,
//                "java/lang/Object",
//                "<init>",
//                "()V",
//                false
//            )
//        }
//
//        val mapped = FunctionMapper().map(header)
//        var added = 0
//        mappedFunction = mapped
//        for(block in mapped.blocks) {
//            mv.visitLabel(block.key)
//            visitBlock(block.value, mv)
//            if(added == 0) {
//                added = 1
//                mv.visitInsn(Opcodes.RETURN)
//            }
//        }
//
//        mv.visitMaxs(100, 100)
//        mv.visitEnd()
//    }
//    fun visitEvent(header: Ast.Event, cw: ClassVisitor) {
//        if(currentClass.name != PathName.parse("ServerEvents")) {
//            throw SQLIntegrityConstraintViolationException()
//        }
//
//        when(header.name) {
//            "join", "startup" -> {
//                val mv = cw.visitMethod(
//                    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
//                    header.name,
//                    "()V",
//                    null,
//                    listOf<String>().toTypedArray()
//                )
//
//                mv.visitCode()
//                val mapped = FunctionMapper().map(header)
//                var added = 0
//                mappedFunction = mapped
//                for(block in mapped.blocks) {
//                    mv.visitLabel(block.key)
//                    visitBlock(block.value, mv)
//                    if(added == 0) {
//                        added = 1
//                        mv.visitInsn(Opcodes.RETURN)
//                    }
//                }
//
//
//                when(currentHeader) {
//                    is Ast.Event -> {
//                        mv.visitInsn(Opcodes.RETURN)
//                    }
//                    is Ast.Function -> {
//                        if((currentHeader as Ast.Function).returns == Type.Void) {
//                            mv.visitInsn(Opcodes.RETURN)
//                        }
//                    }
//                    is Ast.DeclareField -> throw Unreachable()
//                }
//
//
//                mv.visitMaxs(100, 100)
//                mv.visitEnd()
//            }
//            else -> throw SQLIntegrityConstraintViolationException()
//        }
//    }
//    fun visitBlock(block: Ast.Block, mv: MethodVisitor) {
//
//        for(code in block.nodes) {
//            visitAction(code, mv)
//        }
//
//        if(mappedFunction.loops.contains(block)) {
//            mv.visitJumpInsn(Opcodes.GOTO, mappedFunction.flippedBlocks[block])
//        }
//    }
//
//    fun visitAction(action: Ast.Action, mv: MethodVisitor) {
//        when(action) {
//            is Ast.ForEachStatement -> {}
//            is Ast.LoopStatement -> {
//                val label = mappedFunction.flippedBlocks[action.block]
//                currentLoopSurrounding = Label()
//                mv.visitJumpInsn(Opcodes.GOTO, label)
//                mv.visitLabel(currentLoopSurrounding)
//            }
//            is Ast.IfStatement -> {
//                visitValue(action.condition, mv)
//                mv.visitJumpInsn(
//                    Opcodes.IFNE,
//                    mappedFunction.flippedBlocks[action.ifTrue]
//                )
//            }
//            is Ast.FunctionCall -> {
//                when(action.name.resolve()) {
//                    "arraylen" -> {
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        mv.visitInsn(Opcodes.ARRAYLENGTH)
//                    }
//                    "add" -> {
//                        val type = evaluateType(action.arguments[0].argument)
//                        when(type) {
//                            is Type.Number -> {
//                                for(argument in action.arguments) {
//                                    visitValue(argument.argument, mv)
//                                }
//                                mv.visitInsn(Opcodes.DADD)
//                            }
//                            is Type.String -> {
//                                mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
//                                mv.visitInsn(Opcodes.DUP)
//                                mv.visitMethodInsn(
//                                    Opcodes.INVOKESPECIAL,
//                                    "java/lang/StringBuilder",
//                                    "<init>",
//                                    "()V",
//                                    false
//                                )
//                                for(argument in action.arguments) {
//                                    visitValue(argument.argument, mv)
//                                    val vtype = evaluateType(argument.argument)
//                                    if(vtype is Type.Object) {
//                                        mv.visitMethodInsn(
//                                            Opcodes.INVOKEVIRTUAL,
//                                            "java/lang/StringBuilder",
//                                            "append",
//                                            "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
//                                            false
//                                        )
//                                    } else {
//                                        mv.visitMethodInsn(
//                                            Opcodes.INVOKEVIRTUAL,
//                                            "java/lang/StringBuilder",
//                                            "append",
//                                            "(${vtype.toJvmSignature()})Ljava/lang/StringBuilder;",
//                                            false
//                                        )
//                                    }
//
//
//                                }
//                                mv.visitMethodInsn(
//                                    Opcodes.INVOKEVIRTUAL,
//                                    "java/lang/StringBuilder",
//                                    "toString",
//                                    "()Ljava/lang/String;",
//                                    false
//                                )
//                            }
//                            else -> TODO()
//                        }
//                    }
//                    "sub" -> {
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        val type = evaluateType(action.arguments[0].argument)
//                        mv.visitInsn(Opcodes.DSUB)
//                    }
//                    "mul" -> {
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        val type = evaluateType(action.arguments[0].argument)
//                        mv.visitInsn(Opcodes.DMUL)
//                    }
//                    "div" -> {
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        val type = evaluateType(action.arguments[0].argument)
//                        mv.visitInsn(Opcodes.DDIV)
//                    }
//                    "eq" -> {
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        val type = evaluateType(action.arguments[0].argument)
//                        val ifTrueLabel = Label()
//                        val continueLabel = Label()
//                        when(type) {
//                            Type.Boolean -> {
//                                mv.visitJumpInsn(Opcodes.IF_ICMPEQ, ifTrueLabel)
//                                mv.visitLdcInsn(1)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(ifTrueLabel)
//                                mv.visitLdcInsn(0)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(continueLabel)
//                            }
//                            Type.Number -> {
//                                mv.visitInsn(Opcodes.DCMPL)
//                                mv.visitJumpInsn(Opcodes.IFNE, ifTrueLabel)
//                                mv.visitLdcInsn(1)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(ifTrueLabel)
//                                mv.visitLdcInsn(0)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(continueLabel)
//                            }
//                            is Type.Object, Type.String, is Type.Array -> {
//                                mv.visitJumpInsn(Opcodes.IF_ACMPEQ, ifTrueLabel)
//                                mv.visitLdcInsn(1)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(ifTrueLabel)
//                                mv.visitLdcInsn(0)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(continueLabel)
//                            }
//                            Type.Void -> mv.visitLdcInsn(0)
//                        }
//                    }
//                    "ne" -> {
//                        for (argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        val type = evaluateType(action.arguments[0].argument)
//                        val ifTrueLabel = Label()
//                        val continueLabel = Label()
//                        when (type) {
//                            Type.Boolean -> {
//                                mv.visitJumpInsn(Opcodes.IF_ICMPNE, ifTrueLabel)
//                                mv.visitLdcInsn(1)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(ifTrueLabel)
//                                mv.visitLdcInsn(0)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(continueLabel)
//                            }
//
//                            Type.Number -> {
//                                mv.visitInsn(Opcodes.DCMPL)
//                                mv.visitJumpInsn(Opcodes.IFEQ, ifTrueLabel)
//                                mv.visitLdcInsn(1)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(ifTrueLabel)
//                                mv.visitLdcInsn(0)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(continueLabel)
//                            }
//
//                            is Type.Object, Type.String, is Type.Array -> {
//                                mv.visitJumpInsn(Opcodes.IF_ACMPNE, ifTrueLabel)
//                                mv.visitLdcInsn(1)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(ifTrueLabel)
//                                mv.visitLdcInsn(0)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(continueLabel)
//                            }
//
//                            Type.Void -> mv.visitLdcInsn(0)
//                        }
//                    }
//                    "ge" -> {
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        val type = evaluateType(action.arguments[0].argument)
//                        val ifTrueLabel = Label()
//                        val continueLabel = Label()
//                        when(type) {
//                            Type.Number -> {
//                                mv.visitInsn(Opcodes.DCMPL)
//                                mv.visitJumpInsn(Opcodes.IFGE, ifTrueLabel)
//                                mv.visitLdcInsn(1)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(ifTrueLabel)
//                                mv.visitLdcInsn(0)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(continueLabel)
//                            }
//                            else -> TODO()
//                        }
//                    }
//                    "le" -> {
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        val type = evaluateType(action.arguments[0].argument)
//                        val ifTrueLabel = Label()
//                        val continueLabel = Label()
//                        when(type) {
//                            Type.Number -> {
//                                mv.visitInsn(Opcodes.DCMPL)
//                                mv.visitJumpInsn(Opcodes.IFGE, ifTrueLabel)
//                                mv.visitLdcInsn(1)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(ifTrueLabel)
//                                mv.visitLdcInsn(0)
//                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel)
//                                mv.visitLabel(continueLabel)
//                            }
//                            else -> TODO()
//                        }
//                    }
//                    "native.jvm_static_field" -> {
//                        val owner = action.arguments.removeAt(0)
//                        val function = action.arguments.removeAt(0)
//                        val signature = action.arguments.removeAt(0)
//
//                        mv.visitFieldInsn(
//                            Opcodes.GETSTATIC,
//                            (owner.argument as Ast.StringText).value,
//                            (function.argument as Ast.StringText).value,
//                            (signature.argument as Ast.StringText).value
//                        )
//                    }
//                    "native.jvm_static_invoke" -> {
//                        val owner = action.arguments.removeAt(0)
//                        val function = action.arguments.removeAt(0)
//                        val signature = action.arguments.removeAt(0)
//
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        mv.visitMethodInsn(
//                            Opcodes.INVOKESTATIC,
//                            (owner.argument as Ast.StringText).value,
//                            (function.argument as Ast.StringText).value,
//                            (signature.argument as Ast.StringText).value,
//                            false
//                        )
//                    }
//                    "native.jvm_interface_invoke" -> {
//                        val owner = action.arguments.removeAt(0)
//                        val function = action.arguments.removeAt(0)
//                        val signature = action.arguments.removeAt(0)
//
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//                        mv.visitMethodInsn(
//                            Opcodes.INVOKESTATIC,
//                            (owner.argument as Ast.StringText).value,
//                            (function.argument as Ast.StringText).value,
//                            (signature.argument as Ast.StringText).value,
//                            true
//                        )
//                    }
//                    "native.jvm_virtual_invoke" -> {
//                        val owner = action.arguments.removeAt(0)
//                        val function = action.arguments.removeAt(0)
//                        val signature = action.arguments.removeAt(0)
//                        println("""
//                            -----
//                            virtual invocation
//                            owner: $owner
//                            function: $function
//                            signature: $signature
//                            arguments:
//                            ${action.arguments}
//                            -----
//                        """.trimIndent())
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//
//                        mv.visitMethodInsn(
//                            Opcodes.INVOKEVIRTUAL,
//                            (owner.argument as Ast.StringText).value,
//                            (function.argument as Ast.StringText).value,
//                            (signature.argument as Ast.StringText).value,
//                            false
//                        )
//                    }
//                    "return" -> {
//                        if(action.arguments.isNotEmpty()) {
//                            visitValue(action.arguments[0].argument, mv)
//                        }
//
//                        when(currentHeader) {
//                            is Ast.Event -> {
//                                mv.visitInsn(Opcodes.RETURN)
//                            }
//                            is Ast.Function -> {
//                                when((currentHeader as Ast.Function).returns) {
//                                    Type.String -> mv.visitInsn(Opcodes.ARETURN)
//                                    Type.Boolean -> mv.visitInsn(Opcodes.IRETURN)
//                                    is Type.Array -> mv.visitInsn(Opcodes.ARETURN)
//                                    Type.Number -> mv.visitInsn(Opcodes.DRETURN)
//                                    is Type.Object -> mv.visitInsn(Opcodes.ARETURN)
//                                    Type.Void -> mv.visitInsn(Opcodes.RETURN)
//                                }
//                            }
//                            is Ast.DeclareField -> throw Unreachable()
//                        }
//                    }
//                    else -> {
//                        for(argument in action.arguments) {
//                            visitValue(argument.argument, mv)
//                        }
//
//                        val path = action.name.path
//                        val finalFunction = path.removeLast()
//                        val clazz = path.joinToString("/")
//                        path.add(finalFunction)
//
//                        if(functionSignatures.containsKey(action.name)) {
//                            val signature: String = functionSignatures[action.name]!!
//                            mv.visitMethodInsn(
//                                Opcodes.INVOKESTATIC,
//                                clazz,
//                                finalFunction,
//                                signature,
//                                false
//                            )
//                        } else {
//                            val signature =
//                                "(${action.arguments.joinToString("") { evaluateType(it.argument).toJvmSignature() }})${action.returns.toJvmSignature()}"
//                            mv.visitMethodInsn(
//                                Opcodes.INVOKESTATIC,
//                                clazz,
//                                finalFunction,
//                                signature,
//                                false
//                            )
//
//                        }
//
//                    }
//                }
//            }
//            is Ast.MemberAccess -> {
//                val type = localVariableTypes[action.variableName]!! as Type.Object
//
//
//                visitValue(Ast.Variable(action.variableName), mv)
//                for(argument in action.arguments) {
//                    visitValue(argument.argument, mv)
//                }
//
//                val key = PathName.parse(type.signature.resolve() + "." + action.name.resolve())
//                if(functionSignatures.containsKey(key)) {
//                    mv.visitMethodInsn(
//                        Opcodes.INVOKEVIRTUAL,
//                        type.signature.resolve().replace(".", "/"),
//                        action.name.resolve().replace(".", "__"),
//                        functionSignatures[key]!!,
//                        false
//                    )
//                } else {
//                    val signature =
//                        "(${action.arguments.joinToString("") { evaluateType(it.argument).toJvmSignature() }})${action.returnedType.toJvmSignature()}"
//                    mv.visitMethodInsn(
//                        Opcodes.INVOKEVIRTUAL,
//                        type.signature.resolve().replace(".", "/"),
//                        action.name.resolve().replace(".", "__"),
//                        signature,
//                        false
//                    )
//                }
//
//            }
//            is Ast.ConstructClass -> {
//                val clazz = action.className.resolve().replace(".", "/")
//                mv.visitTypeInsn(
//                    Opcodes.NEW,
//                    clazz,
//                )
//                mv.visitInsn(Opcodes.DUP)
//                for(argument in action.arguments) {
//                    visitValue(argument.argument, mv)
//                }
//                mv.visitMethodInsn(
//                    Opcodes.INVOKESPECIAL,
//                    clazz,
//                    "<init>",
//                    "(${action.arguments.joinToString("") { evaluateType(it.argument).toJvmSignature() }})V",
//                    false
//                )
//            }
//            is Ast.StoreVariable -> {
//                for(header in currentClass.headers) {
//                    if(header is Ast.DeclareField && header.name == action.name && !currentClass.static) {
//                        mv.visitVarInsn(Opcodes.ALOAD, 0)
//                        visitValue(action.value, mv)
//                        mv.visitFieldInsn(
//                            Opcodes.PUTFIELD,
//                            currentClass.name.resolve().replace(".", "/"),
//                            action.name,
//                            header.type.toJvmSignature(),
//                        )
//                        return
//                    }
//                }
//                visitValue(action.value, mv)
//                val type = localVariableTypes[action.name]!!
//                when(type) {
//                    Type.String -> mv.visitVarInsn(
//                        Opcodes.ASTORE,
//                        localVariableIndices[action.name]!!
//                    )
//
//                    Type.Boolean -> mv.visitVarInsn(
//                        Opcodes.ISTORE,
//                        localVariableIndices[action.name]!!
//                    )
//
//                    is Type.Array -> mv.visitVarInsn(
//                        Opcodes.ASTORE,
//                        localVariableIndices[action.name]!!
//                    )
//
//                    Type.Number -> mv.visitVarInsn(
//                        Opcodes.DSTORE,
//                        localVariableIndices[action.name]!!
//                    )
//
//                    is Type.Object -> mv.visitVarInsn(
//                        Opcodes.ASTORE,
//                        localVariableIndices[action.name]!!
//                    )
//
//                    Type.Void -> TODO()
//                }
//            }
//            is Ast.DeclareVariable -> {
//                visitValue(action.value, mv)
//                localVariableTypes[action.name] = action.type
//                localVariableIndex += 2
//                localVariableIndices[action.name] = localVariableIndex
//                when(action.type) {
//                    Type.String -> mv.visitVarInsn(
//                        Opcodes.ASTORE,
//                        localVariableIndices[action.name]!!
//                    )
//                    Type.Boolean -> mv.visitVarInsn(
//                        Opcodes.ISTORE,
//                        localVariableIndices[action.name]!!
//                    )
//                    is Type.Array -> mv.visitVarInsn(
//                        Opcodes.ASTORE,
//                        localVariableIndices[action.name]!!
//                    )
//                    Type.Number -> mv.visitVarInsn(
//                        Opcodes.DSTORE,
//                        localVariableIndices[action.name]!!
//                    )
//                    is Type.Object -> mv.visitVarInsn(
//                        Opcodes.ASTORE,
//                        localVariableIndices[action.name]!!
//                    )
//                    Type.Void -> TODO()
//                }
//
//            }
//            is Ast.BreakStatement -> {
//                mv.visitJumpInsn(Opcodes.GOTO, currentLoopSurrounding)
//            }
//        }
//    }
//
//    fun visitValue(value: Ast.Value, mv: MethodVisitor) {
//        when(value) {
//            is Ast.Number -> mv.visitLdcInsn(value.value)
//            is Ast.Boolean -> if(value.value) mv.visitLdcInsn(1) else mv.visitLdcInsn(0)
//            is Ast.FunctionCall, is Ast.ConstructClass, is Ast.MemberAccess -> visitAction(value as Ast.Action, mv)
//            Ast.Null -> mv.visitInsn(Opcodes.NULL)
//            is Ast.StringText -> mv.visitLdcInsn(value.value)
//            is Ast.Variable -> {
//                var isField = false
//                var fieldSignature = ""
//                currentClass.headers.forEach {
//                    if(it is Ast.DeclareField && it.name == value.value) {
//                        isField = true
//                        fieldSignature = it.type.toJvmSignature()
//                    }
//                }
//                if(isField) {
//                    mv.visitVarInsn(Opcodes.ALOAD, 0)
//                    mv.visitFieldInsn(
//                        Opcodes.GETFIELD,
//                        currentClass.name.resolve().replace(".", "/"),
//                        value.value,
//                        fieldSignature
//                    )
//                    return
//                }
//                if(globalVariables.containsKey(value.value)) {
//                    println("${value.value} is a global im ngl, putting in the get field rn")
//                    println("what else?! ${currentClass.name.resolve().replace(".", "/")} ${value.value}")
//                    println("brother! ${globalVariables[value.value]!!.toJvmSignature()}")
//                    mv.visitFieldInsn(
//                        Opcodes.GETSTATIC,
//                        currentClass.name.resolve().replace(".", "/"),
//                        value.value,
//                        globalVariables[value.value]!!.toJvmSignature()
//                    )
//                    return
//                }
//                when(localVariableTypes[value.value]!!) {
//                    Type.String -> mv.visitVarInsn(Opcodes.ALOAD, localVariableIndices[value.value]!!)
//                    Type.Boolean -> mv.visitVarInsn(Opcodes.ILOAD, localVariableIndices[value.value]!!)
//                    is Type.Array -> mv.visitVarInsn(Opcodes.ALOAD, localVariableIndices[value.value]!!)
//                    Type.Number -> mv.visitVarInsn(Opcodes.DLOAD, localVariableIndices[value.value]!!)
//                    is Type.Object -> mv.visitVarInsn(Opcodes.ALOAD, localVariableIndices[value.value]!!)
//                    Type.Void -> TODO()
//                }
//            }
//
//            is Ast.ArrayOf -> {
//                mv.visitLdcInsn(value.arguments.size)
//                when(value.type) {
//                    is Type.Array -> mv.visitTypeInsn(Opcodes.ANEWARRAY, value.type.toJvmSignature())
//                    Type.Boolean -> mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
//                    Type.Number -> mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE)
//                    is Type.Object -> mv.visitTypeInsn(Opcodes.ANEWARRAY, value.type.toJvmSignature())
//                    Type.String -> mv.visitTypeInsn(Opcodes.ANEWARRAY, value.type.toJvmSignature())
//                    Type.Void -> TODO()
//                }
//
//                for((index, argument) in value.arguments.withIndex()) {
//                    mv.visitInsn(Opcodes.DUP)
//                    mv.visitLdcInsn(index)
//                    visitValue(argument.argument, mv)
//
//                    println("arraying argument: ${argument.argument}")
//                    when(argument.argument) {
//                        is Ast.ArrayOf -> mv.visitInsn(Opcodes.AASTORE)
//                        is Ast.Boolean -> mv.visitInsn(Opcodes.IASTORE)
//                        is Ast.ConstructClass -> mv.visitInsn(Opcodes.AASTORE)
//                        is Ast.FunctionCall -> when(functionReturnTypes[argument.argument.name]!!) {
//                            is Type.Array -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.Boolean -> mv.visitInsn(Opcodes.IASTORE)
//                            Type.Number -> mv.visitInsn(Opcodes.FASTORE)
//                            is Type.Object -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.String -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.Void -> TODO()
//                        }
//                        // Depends on the return type of the member call...
//                        is Ast.MemberAccess -> when(functionReturnTypes[
//                            PathName.parse(
//                                (localVariableTypes[argument.argument.variableName] as Type.Object)
//                                    .signature.resolve() + "." + argument.argument.name.resolve())]!!) {
//                            is Type.Array -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.Boolean -> mv.visitInsn(Opcodes.IASTORE)
//                            Type.Number -> mv.visitInsn(Opcodes.DASTORE)
//                            is Type.Object -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.String -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.Void -> TODO()
//                        }
//                        Ast.Null -> TODO()
//                        is Ast.Number -> mv.visitInsn(Opcodes.DASTORE)
//                        is Ast.StringText -> mv.visitInsn(Opcodes.AASTORE)
//                        // Depends on the variable's type...
//                        is Ast.Variable -> when(localVariableTypes[argument.argument.value]!!) {
//                            is Type.Array -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.Boolean -> mv.visitInsn(Opcodes.IASTORE)
//                            Type.Number -> mv.visitInsn(Opcodes.DASTORE)
//                            is Type.Object -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.String -> mv.visitInsn(Opcodes.AASTORE)
//                            Type.Void -> TODO()
//                        }
//                    }
//
//                }
//            }
//        }
//    }
//}