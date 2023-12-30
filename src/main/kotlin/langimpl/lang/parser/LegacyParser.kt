package langimpl.lang.parser

import langimpl.error.UnexpectedEOF
import langimpl.error.UnexpectedToken
import langimpl.error.Unreachable
import langimpl.lang.lexer.SpanData
import langimpl.lang.lexer.Token
import parser.Ast
import parser.CommandArgument
import parser.PathName
import parser.Type
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLWarning

class LegacyParser(private val input: MutableList<Token>) {
    var pointer = -1
    var lastNext: Token = Token.Identifier("lkhdaskjld", SpanData(0, 0, "somethign went wrong fhere"))
    var blockId = 0

    fun hasNext(): Boolean {
        return input.getOrNull(pointer+1) != null
    }

    private fun peek(ignoreWhitespace: Boolean = true, amount: Int = 0): Token {
        var b = pointer
        if(ignoreWhitespace) {
            if(pointer > 0) {
                val startTok = input[b]
                if(b+amount > input.size) throw UnexpectedEOF(startTok.span)
            }

            var p = input[++b+amount]
            while(p is Token.NewLine) {
                if(b+1+amount > input.size) throw UnexpectedEOF(p.span)
                p = input[++b+amount]
            }

            return p
        } else {
            val startTok = input[b]
            if(b+amount >= input.size) throw UnexpectedEOF(startTok.span)
            return input[b+amount]
        }
    }





    fun next(ignoreWhitespace: Boolean = true): Token {
        if(ignoreWhitespace) {
            if(pointer > 0) {
                val startTok = input[pointer]
                if(pointer+1 > input.size) throw UnexpectedEOF(startTok.span)
            }
            val ptr = ++pointer
            if(ptr > input.size-1) {
                throw UnexpectedEOF(input.last().span)
            }
            var p = input[ptr]
            while(p is Token.NewLine) {
                if(pointer+1 > input.size) throw UnexpectedEOF(p.span)
                p = input[++pointer]
            }
            println("next: $p")
            try {
                throw SQLWarning()
            } catch(e: SQLWarning) {
                println(e.stackTraceToString())
            }
            return p
        } else {
            val startTok = input[pointer]
            if(pointer+1 > input.size) throw UnexpectedEOF(startTok.span)
            println("next: ${input[++pointer]}")
            try {
                throw SQLWarning()
            } catch(e: SQLWarning) {
                println(e.stackTraceToString())
            }
            return input[++pointer]
        }
    }

    fun parseAll(): Ast.Program {
        val output = mutableListOf<Ast.Class>()
        while(true) {
            if(peek() is Token.EOF)
                break
            val header = parseClass()
            output.add(header)
        }

        return Ast.Program(output)
    }

    private fun parseHeader(): Pair<Boolean, Ast.Header?> {
        when(val front = next()) {
            is Token.BuiltinKeyword -> {
                return parseHeader()
            }
            is Token.LetKeyword -> {
                val identifier = next()
                if(identifier !is Token.Identifier) {
                    throw UnexpectedToken("identifier", identifier, identifier.span)
                }
                val colon = next()
                if(colon !is Token.Colon) {
                    throw UnexpectedToken("colon", identifier, identifier.span)
                }
                val type = parseType()
                val equals = next()
                println("equals = $equals")
                if(equals !is Token.Equals)
                    throw UnexpectedToken("equals", identifier, identifier.span)
                val peeked = peek(false)
                val value = parseValue()
                println("value: $value")
                if(value == null)
                    throw UnexpectedToken("valid value", peeked, peeked.span)
                return Pair(true, Ast.DeclareField(identifier.value, value, identifier.span, type))
            }
            is Token.EventKeyword -> {
                return Pair(true, parseEvent())
            }
            is Token.CommandKeyword -> {
                return Pair(true, parseFunction())
            }
            is Token.EOF -> {
                return Pair(false, null)
            }
            is Token.CloseBrace -> {
                return Pair(false, null)
            }
            is Token.At -> {
                return Pair(true, parseAnnotation())
            }
            else -> throw UnexpectedToken("valid header keyword", front, front.span)
        }
    }

    private fun parseAnnotation(): Ast.Annotation {
        val nameToken = peek().span
        val name = parseNamespacedIdentifier()
        return Ast.Annotation(name, mutableListOf<CommandArgument>(), nameToken)
    }
    private fun parseEvent(): Ast.Event {
        val eventNameToken = next()
        if(eventNameToken !is Token.Identifier) {
            throw UnexpectedToken("identifier", eventNameToken, eventNameToken.span)
        }
        return Ast.Event(
            eventNameToken.value,
            parseBlock(eventNameToken.value),
            eventNameToken.span
        )
    }

    private fun parseFunction(): Ast.Function {
        val rawToken = peek()
        val functionNameToken = parseNamespacedIdentifier()

        val arguments = mutableMapOf<String, Type>()
        while (true) {
            val arg = peek()
            if (arg !is Token.Identifier)
                break
            val name = next() as Token.Identifier
            val colon = next()
            if(colon !is Token.Colon)
                throw UnexpectedToken("colon", colon, colon.span)
            val type = parseType()
            arguments[name.value] = type
            val peek = peek()
            if (peek is Token.Arrow) {
                break
            }
            next()
        }

        val endArrow = next()
        if (endArrow !is Token.Arrow)
            throw UnexpectedToken("arrow", endArrow, endArrow.span)

        val type = parseType()
        return Ast.Function(
            functionNameToken,
            parseBlock("function"),
            rawToken.span,
            arguments,
            type
        )
    }

    private fun parseClass(): Ast.Class {
        val kwSpan: SpanData
        val rkw: Token
        var isNative = false
        while(true) {
            val kw = next()
            if(kw is Token.At) {
                val ident = parseNamespacedIdentifier()
                if(ident.resolve() == "native")
                    isNative = true
            }
            if(kw is Token.ClassKeyword || kw is Token.NamespaceKeyword) {
                kwSpan = kw.span
                rkw = kw
                break
            }
        }
        val className = parseNamespacedIdentifier()
        println("PARSING CLASS $className")
        val generics = mutableListOf<Type>()
        if(peek() is Token.LessThan) {
            next()
            while(true) {
                val type = parseType()
                generics.add(type)
                when(peek()) {
                    is Token.GreaterThan -> { next(); break }
                    is Token.Comma -> next()
                    else -> throw UnexpectedToken("greater than symbol or comma symbol", peek(), peek().span)
                }
            }
        }

        var inheritType: Type? = Type.Object(
            PathName.parse("java.lang.Object"),
            listOf(),
            false
        )
        if(peek() is Token.Colon) {
            next()
            inheritType = parseType() as? Type.Object
            if(inheritType == null)
                throw SQLIntegrityConstraintViolationException()
        }
        println("INHERITS FROM ${inheritType}")

        val openParen = next()
        if(openParen !is Token.OpenBrace) {
            throw UnexpectedToken("open braces", openParen, openParen.span)
        }
        val headers = mutableListOf<Ast.Header>()
        while(true) {
            println("are we done? ${peek()}")
            if(peek() is Token.CloseBrace) break
            println("nope.")
            val h = parseHeader().second!!
            println("h: $h")
            headers.add(h)
        }
        println("DONE!")
        val closeParen = next()
        if(closeParen !is Token.CloseBrace) {
            throw UnexpectedToken("close braces", closeParen, closeParen.span)
        }
        return Ast.Class(className, generics, headers, rkw is Token.NamespaceKeyword, (inheritType as Type.Object), isNative)
    }

    private fun parseBlock(eventName: String = "callable"): Ast.Block {
        val openBrace = next()
        if(openBrace !is Token.OpenBrace) {
            throw UnexpectedToken("opening brace", openBrace, openBrace.span)
        }
        val commands = mutableListOf<Ast.Action>()
        while(true) {
            val peeked = peek()
            if(peeked is Token.CloseBrace)
                break
            val command = parseAction()
            commands.add(command)
        }

        val closeBrace = next()
        if(closeBrace !is Token.CloseBrace) {
            throw UnexpectedToken("closing brace", closeBrace, closeBrace.span)
        }

        return Ast.Block(commands, eventName, openBrace.span)
    }

    private fun parseAction(): Ast.Action {
        println("when you peek: ${peek()}")
        when(val identifier = peek()) {
            is Token.LoopKeyword -> {
                next()
                val block = parseBlock()
                return Ast.LoopStatement(block)
            }
            is Token.BreakKeyword -> {
                next()
                return Ast.BreakStatement
            }
            is Token.NewKeyword -> {
                val n = next()
                val clazzSpan = peek().span
                val clazz = parseNamespacedIdentifier()
                val arguments = mutableListOf<CommandArgument>()
                while(true) {
                    val peeked = peek()
                    val value = parseValue()
                    if(value == null)
                        break
                    else {
                        arguments.add(CommandArgument(
                            value, peeked.span
                        ))
                    }
                }
                return Ast.ConstructClass(clazz, arguments, clazzSpan)
            }
            is Token.LetKeyword -> {
                next()
                return parseVariableDeclare()
            }
            is Token.Identifier -> {
                next()
                val nextToken = next()
                pointer -= 2
                return when(nextToken) {
                    is Token.Arrow -> parseMemberAccess()
                    is Token.Equals -> parseVariableStore()
                    else -> parseStaticAccess()
                }
            }
            is Token.ForEachKeyword -> {
                return parseForEach()
            }
            is Token.IfKeyword -> {
                return parseIf()
            }
            else -> throw UnexpectedToken("valid expression", identifier, identifier.span)
        }
    }

    private fun parseVariableStore(): Ast.StoreVariable {
        val name = next()
        if(name !is Token.Identifier)
            throw SQLIntegrityConstraintViolationException()
        val equals = next()
        if(equals !is Token.Equals)
            throw SQLIntegrityConstraintViolationException()
        val value = parseValue()!!
        return Ast.StoreVariable(name.value, value, name.span)
    }

    private fun parseIf(): Ast.IfStatement {
        val ident = next()
        if(ident !is Token.IfKeyword)
            throw Unreachable()
        val condition = parseValue()!!
        val ifTrue = parseBlock()
        return Ast.IfStatement(condition, ifTrue)
    }
    private fun parseForEach(): Ast.ForEachStatement {
        val ident = next()
        if(ident !is Token.ForEachKeyword)
            throw Unreachable()
        val variable = next()
        if(variable !is Token.Identifier)
            throw UnexpectedToken("identifier", variable, variable.span)
        val colon = next()
        if(colon !is Token.Colon)
            throw UnexpectedToken("colon", colon, colon.span)
        val value = parseValue() ?: throw UnexpectedToken("value", colon, colon.span)
        val ifTrue = parseBlock()
        return Ast.ForEachStatement(variable.value, value, ifTrue)
    }
    private fun parseNamespacedIdentifier(path: PathName = PathName(mutableListOf())): PathName {
        val identifier = next()
        if(identifier !is Token.Identifier)
            throw UnexpectedToken("identifier", identifier, identifier.span)

        path.path.add(identifier.value)

        val next = peek()
        if(next is Token.Dot) {
            next()
            return parseNamespacedIdentifier(path)
        }
        return path
    }

    private fun parseType(): Type {
        val identifier = parseNamespacedIdentifier()

        val generics = mutableListOf<Type>()
        if(peek() is Token.LessThan) {
            next()
            generics.add(parseType())
            while(true) {
                when(peek()) {
                    is Token.GreaterThan -> {
                        next()
                        break
                    }
                    is Token.Comma -> {
                        next()
                        generics.add(parseType())
                    }
                    else -> throw UnexpectedToken("comma or close bracket", peek(), peek().span)
                }
            }
        }
        return when(identifier.resolve()) {
            "string" -> Type.String
            "boolean" -> Type.Boolean
            "void" -> Type.Void
            "number" -> Type.Number
            "array" -> Type.Array(generics[0])
            else -> Type.Object(identifier, generics)
        }
    }
    private fun parseVariableDeclare(): Ast.DeclareVariable {
        val identifier = next()
        if(identifier !is Token.Identifier)
            throw SQLIntegrityConstraintViolationException()

        val colon = next()
        if(colon !is Token.Colon)
            throw SQLIntegrityConstraintViolationException()

        val type = parseType()
        val equals = next()
        if(equals !is Token.Equals)
            throw SQLIntegrityConstraintViolationException()

        val value = parseValue() ?: throw SQLIntegrityConstraintViolationException()

        return Ast.DeclareVariable(identifier.value, value, identifier.span, type)
    }

    private fun parseMemberAccess(): Ast.MemberAccess {
        val rawToken = peek()
        val variable = next()
        if(variable !is Token.Identifier)
            throw Unreachable()

        val arrow = next()
        if(arrow !is Token.Arrow)
            throw SQLIntegrityConstraintViolationException()
        val isField = (arrow.isSquiggly)
        val peek = peek()
        var inferredType: Type = Type.Void
        if(peek is Token.OpenParen) {
            next()
            inferredType = parseType()
            if(next() !is Token.CloseParen)
                throw SQLIntegrityConstraintViolationException()
        }

        val identifier = parseNamespacedIdentifier()

        val arguments = mutableListOf<CommandArgument>()
        while(true) {
            val peeked = peek()
            val value = parseValue()
            if(value == null)
                break
            else {
                arguments.add(CommandArgument(
                    value, peeked.span
                ))
            }
        }

        return Ast.MemberAccess(identifier, arguments, rawToken.span, variable.span, variable.value, inferredType, !isField)
    }

    private fun parseStaticAccess(): Ast.StaticAccess {
        val rawToken = peek()
        val identifier = parseNamespacedIdentifier()
        val arguments = mutableListOf<CommandArgument>()
        var returnType: Type = Type.Void
        var isFunction = false
        if(peek() is Token.Colon) {
            isFunction = true
            val c2 = next()
            if(c2 !is Token.Colon)
                throw UnexpectedToken("colon", c2, c2.span)
            val c3 = next()
            if(c3 !is Token.Colon)
                throw UnexpectedToken("colon", c3, c3.span)
            if(peek() is Token.OpenParen) {
                next()
                returnType = parseType()
                if(next() !is Token.CloseParen)
                    throw SQLIntegrityConstraintViolationException()
            }

            val name = next()
            if(name !is Token.Identifier)
                throw UnexpectedToken("function name", name, name.span)
            identifier.path.add(name.value)
        }

        if(peek() is Token.LessThan) {
            val lt = next()
            returnType = parseType()
            val gt = next()
            if(gt !is Token.GreaterThan)
                throw SQLIntegrityConstraintViolationException()
        }

        while(true) {
            val peeked = peek()
            val value = parseValue()
            if(value == null)
                break
            else {
                arguments.add(CommandArgument(
                    value, peeked.span
                ))
            }
            if(peek() !is Token.Comma)
                break
        }
        return Ast.StaticAccess(identifier, arguments, rawToken.span, returnType, isFunction)
    }

    private fun parseValue(): Ast.Value? {
        val sub = parseSubValue()
//        if(peek() is Token.OpenBracket) {
//            val idSpan = peek().span
//            val id = parseValue()
//            val close = next()
//            if(close !is Token.CloseBracket)
//                throw UnexpectedToken("closing bracket", close, close.span)
//            return Ast.StaticAccess(
//                PathName.parse("jvmarrayindex"),
//                mutableListOf(CommandArgument(sub!!, valueSpan), CommandArgument(id!!, idSpan)),
//                valueSpan,
//                Type.Void,
//                true
//            )
//        }
        println("sub = $sub")
        return sub
    }

    private fun parseSubValue(): Ast.Value? {
        val next = next(false)
        println("next = $next")
        return when(next) {
            is Token.OpenBracket -> {
                val type = parseType()
                val colon = next()
                if(colon !is Token.Colon)
                    throw SQLIntegrityConstraintViolationException()
                val arguments = mutableListOf<CommandArgument>()
                while(true) {
                    val p = peek()
                    val v = parseValue()!!

                    val cmdArg = CommandArgument(
                        v,
                        p.span
                    )
                    arguments.add(cmdArg)

                    val peek = peek()
                                        if(peek is Token.CloseBracket) {
                        break
                    }
                }
                val next2 = next()
                                if(next2 !is Token.CloseBracket)
                    throw SQLIntegrityConstraintViolationException()
                return Ast.ArrayOf(type, arguments, colon.span)
            }
            is Token.StringText -> Ast.StringText(next.value)
            is Token.Number -> Ast.Number(next.value)
            is Token.OpenParen -> {
                val tmp = parseAction() as Ast.Value
                val peek = peek()
                if(peek is Token.CloseParen && tmp !is Ast.StaticAccess) {
                    next()
                }
                return tmp
            }
            is Token.Identifier -> when(next.value) {
                "true" -> Ast.Boolean(true)
                "false" -> Ast.Boolean(false)
                "null" -> Ast.Null
                else -> Ast.Variable(next.value)
            }
            is Token.CloseParen -> {
                println("CLOSE PAREN!!!")
                return null
            }
            is Token.NewLine -> {
                println("NEW LINE!!!")
                return null
            }
            else -> {
                throw UnexpectedToken("valid value", next, next.span)
            }
        }
    }
}

