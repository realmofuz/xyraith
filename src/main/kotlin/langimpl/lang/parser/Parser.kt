package langimpl.lang.parser

import langimpl.error.UnexpectedToken
import langimpl.lang.lexer.Token
import parser.Ast
import parser.CommandArgument
import parser.PathName
import parser.Type
import java.lang.IndexOutOfBoundsException
import kotlin.math.exp

class Parser(private val tokens: List<Token>) {
    var pointer = 0

    /*
    Debug method to print the current location in the code.
     */
    fun printStackTrace() {
//        try {
//            throw SQLWarning()
//        } catch(e: SQLWarning) {
//            println(e.stackTraceToString())
//        }
    }
    /*
    Parse a series of tokens into a valid AST.
    Throws `SyntaxError` if invalid.
     */
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

    fun nextWrapped(ignoreWhitespace: Boolean = true): Token {
        try {
            if(ignoreWhitespace && tokens[pointer] is Token.NewLine) {
                while(tokens[pointer] is Token.NewLine) {
                    pointer++
                }
            }
            return tokens[pointer++]
        } catch(e: IndexOutOfBoundsException) {
            return Token.EOF(tokens.last().span)
        }

    }
    /*
    Grabs the next token available.
     */

    fun next(ignoreWhitespace: Boolean = true): Token {
        val n = nextWrapped(ignoreWhitespace)
        printStackTrace()
        return n
    }

    private fun peekWrapped(ignoreWhitespace: Boolean = true): Token {
        try {
            var tempPointer = pointer
            if(ignoreWhitespace && tokens[tempPointer] is Token.NewLine) {
                while(tokens[tempPointer] is Token.NewLine) {
                    tempPointer++
                }
            }
            return tokens[tempPointer]
        } catch(e: IndexOutOfBoundsException) {
            return Token.EOF(tokens.last().span)
        }
    }

    /*
    Peeks into the next token without advancing the pointer.
     */
    fun peek(ignoreWhitespace: Boolean = true): Token {
        val n = peekWrapped(ignoreWhitespace)
        printStackTrace()
        return n
    }

    /*
    Call next() with a specific token required.
    Also automatically casts that token if it is valid.
    Throws `UnexpectedToken` if not the correct type.
     */
    private inline fun<reified T: Token> expect(expected: String): T {
        val next = next()
        if(next !is T) {
            throw UnexpectedToken(expected, next, next.span)
        }
        return next
    }

    /*
    Parse an annotation.
    These go above valid headers and look like this:
    @<path name>
    e.g
    @static
    @inline
    @native
    etc.
     */
    fun parseAnnotation(): Ast.Annotation? {
        val at = peek()
        if(at is Token.At) {
            expect<Token.At>("at symbol")
            val identifier = parsePathName()
            return Ast.Annotation(
                identifier,
                mutableListOf(),
                at.span
            )
        } else {
            return null
        }
    }

    /*
    Parse a class.
    They look like this:
    (class|namespace) (PathName name) (: (extension type))? {
        // lots of headers go here...
    }
     */
    fun parseClass(): Ast.Class {
        val annotations: MutableList<PathName> = mutableListOf()
        while(true) {
            annotations.add(parseAnnotation()?.name ?: break)
        }

        val keyword = next()
        if(keyword is Token.ClassKeyword || keyword is Token.NamespaceKeyword) {
            val isStaticClass = keyword is Token.NamespaceKeyword
            val name = parsePathName()
            var extends: Type.Object = Type.Object(
                PathName.parse("java.lang.Object"),
                listOf(),
                false
            )
            var interfaces: MutableList<Type.Object> = mutableListOf()
            while(true) {
                when {
                    peek() is Token.ExtendsKeyword -> {
                        expect<Token.ExtendsKeyword>("extends")
                        extends = parseType() as Type.Object
                    }
                    peek() is Token.ImplementsKeyword -> {
                        expect<Token.ImplementsKeyword>("implements")
                        while(true) {
                            val type = parseType() as Type.Object
                            interfaces.add(type)
                            if(peek() !is Token.Comma)
                                break
                            val comma = expect<Token.Comma>("comma")
                        }
                    }
                    else -> break
                }
            }
            val headers = mutableListOf<Ast.Header>()
            expect<Token.OpenBrace>("open braces")
            while(true) {
                if(peek() is Token.CloseBrace)
                    break
                val header = parseHeader()
                headers.add(header)
            }
            expect<Token.CloseBrace>("close braces")

            return Ast.Class(
                name,
                listOf(),
                headers,
                isStaticClass,
                extends,
                annotations.contains(PathName.parse("native")),
                annotations.contains(PathName.parse("interface")),
                keyword.span,
                interfaces
            )
        } else {
            throw UnexpectedToken("class or namespace keyword", keyword, keyword.span)
        }
    }

    /*
    Parse a valid type.
    Types are a path name, followed by more types inside these brackets: <>.
    E.g array<number>, string, map<string, number>, java.lang.String, etc.
     */
    fun parseType(): Type {
        val mainName = parsePathName()
        val generics = mutableListOf<Type>()
        if(peek() is Token.LessThan) {
            while(true) {
                expect<Token.LessThan>("less than")
                generics.add(parseType())
                if(peek() is Token.GreaterThan) {
                    break
                }
                expect<Token.Comma>("comma")
            }
            expect<Token.GreaterThan>("less than")
        }
        return when(mainName.resolve()) {
            "number" -> Type.Number
            "string" -> Type.Object(
                PathName.parse("java.lang.String"),
                listOf(),
                false
            )
            "void", "auto" -> Type.Void
            "array" -> Type.Array(generics[0])
            "any" -> Type.Object(
                PathName.parse("java.lang.Object"),
                listOf(),
                false
            )
            else -> Type.Object(
                mainName,
                generics,
                false
            )
        }
    }
    /*
    Parse a valid header.
    This includes functions, events, annotations, and field declarations.
    Annotate them with @static to make them static when they normally aren't,
    and annotate them with @native to indicate the JVM/JDK implements the behavior
    for you.
     */
    fun parseHeader(): Ast.Header {
        when(val keyword = peek()) {
            is Token.At -> {
                return parseAnnotation()!!
            }
            is Token.LetKeyword -> {
                val letKeyword = expect<Token.LetKeyword>("let keyword")
                val fieldSpan = peek().span
                val fieldName = parseSafeIdentifier()
                val colon = expect<Token.Colon>("colon")
                val type = parseType()
                val equals = expect<Token.Equals>("equals")
                val value = parseValue()
                return Ast.DeclareField(
                    fieldName,
                    value,
                    fieldSpan,
                    type,
                )
            }
            is Token.CommandKeyword -> {
                expect<Token.CommandKeyword>("command keyword")
                val nameSpan = peek().span
                val name = parseSafeIdentifier()
                val arguments = mutableMapOf<String, Type>()
                while(true) {
                    if(peek() is Token.Arrow)
                        break
                    val argumentName = parseSafeIdentifier()
                    val colon = expect<Token.Colon>("colon")
                    val type = parseType()
                    arguments[argumentName] = type
                }
                expect<Token.Arrow>("arrow")
                val returnType = parseType()

                return Ast.Function(
                    PathName.parse(name),
                    parseBlock(),
                    nameSpan,
                    arguments,
                    returnType
                )
            }
            is Token.OnKeyword -> {
                val eventKeyword = expect<Token.OnKeyword>("event keyword")
                val eventName = expect<Token.Identifier>("event name")
                val block = parseBlock()
                return Ast.Event(
                    eventName.value,
                    block,
                    eventName.span
                )
            }
            else -> throw UnexpectedToken("valid header keyword", keyword, keyword.span)
        }
    }

    /*
    Parse a block of actions.
    e.g {
        // actions...
    }
     */
    fun parseBlock(): Ast.Block {
        val open = expect<Token.OpenBrace>("open brace")
        val actions = mutableListOf<Ast.Action>()
        while(true) {
            if(peek() is Token.CloseBrace) {
                break
            }
            actions.add(parseAction())
        }
        expect<Token.CloseBrace>("close brace")
        return Ast.Block(actions, "callable", open.span)
    }

    /*
    Returns one of the valid actions, to get a specific one use other methods.
     */
    fun parseAction(): Ast.Action {
        return when(val next = peek()) {
            is Token.Identifier -> {
                pointer++
                if(peek() is Token.Equals) {
                    pointer--
                    TODO()
                } else {
                    pointer--
                    parseAccess()
                }
            }
            is Token.LetKeyword -> {
                expect<Token.LetKeyword>("let keyword")
                val name = expect<Token.Identifier>("identifier")
                val type = if(peek() is Token.Colon) {
                    expect<Token.Colon>("colon")
                    parseType()
                } else {
                    Type.Void
                }
                val equals = expect<Token.Equals>("equals")
                val value = parseValue()
                return Ast.DeclareVariable(
                    name.value,
                    value,
                    name.span,
                    type
                )
            }
            is Token.IfKeyword -> {
                val ifKeyword = expect<Token.IfKeyword>("if keyword")
                val condition = parseValue()
                val block = parseBlock()
                return Ast.IfStatement(
                    condition, block
                )
            }
            is Token.LoopKeyword -> {
                val loopKeyword = expect<Token.LoopKeyword>("loop keyword")
                val block = parseBlock()
                return Ast.LoopStatement(
                    block
                )
            }
            is Token.NewKeyword -> {
                val kw = expect<Token.NewKeyword>("new keyword")
                val classSpan = peek()
                val type = parseType() as Type.Object
                val values = mutableListOf<CommandArgument>()
                while(true) {
                    if(peek(false) is Token.NewLine || peek() is Token.CloseParen)
                        break
                    val span = peek().span
                    val value = parseValue()
                    values.add(CommandArgument(value, span))
                }
                return Ast.ConstructClass(type.signature, values, classSpan.span)
            }
            else -> throw UnexpectedToken("valid action", next, next.span)
        }
    }
    /*
    Parse an access to a field or method.
    Syntax:
    x.y.z <values>
     */
    private fun parseAccess(): Ast.Access {
        val nameSpan = peek().span
        val name = parsePathName()
        val args = mutableListOf<CommandArgument>()
        while(true) {
            if(
                peek(false) is Token.NewLine
                || peek(false) is Token.CloseParen)
                break
            val span = peek().span
            val value = parseValue()
            args.add(CommandArgument(value, span))

        }
        return Ast.Access(
            name,
            args,
            nameSpan,
            Type.Void
        )
    }

    private fun parseValue(): Ast.Value {
        return when(val next = next()) {
            is Token.OpenBracket -> {
                val arguments = mutableListOf<CommandArgument>()
                while(true) {
                    if(peek() is Token.CloseBracket)
                        break
                    val span = peek().span
                    val value = parseValue()
                    arguments.add(CommandArgument(value, span))
                }
                expect<Token.CloseBracket>("closing bracket")
                return Ast.ArrayOf(Type.Void, arguments, next.span)
            }
            is Token.Number -> Ast.Number(next.value)
            is Token.OpenParen -> {
                val a = parseAction()
                expect<Token.CloseParen>("close parenthesis")
                a as Ast.Value
            }
            is Token.StringText -> Ast.StringText(next.value)
            is Token.Identifier -> {
                when(next.value) {
                    "true" -> Ast.Boolean(true)
                    "false" -> Ast.Boolean(false)
                    else -> Ast.Variable(next.value)
                }
            }
            is Token.ArrayOfKeyword -> TODO()
            else -> throw UnexpectedToken("valid value", next, next.span)
        }
    }

    /*
    Parse a path name.
    e.g x.y.z -> PathName(["x", "y", "z"])
     */
    fun parsePathName(pathName: PathName = PathName(mutableListOf())): PathName {

        val newIdentifier = parseSafeIdentifier()
        if(peek() is Token.Dot) {
            expect<Token.Dot>("period")
            return parsePathName(PathName((pathName.path + newIdentifier).toMutableList()))
        }
        return PathName((pathName.path + newIdentifier).toMutableList())
    }

    fun parseSafeIdentifier(): String {
        return when(val v = next()) {
            is Token.BreakKeyword -> "break"
            is Token.BuiltinKeyword -> "builtin"
            is Token.ClassKeyword -> "class"
            is Token.CommandKeyword -> "command"
            is Token.OnKeyword -> "event"
            is Token.ForEachKeyword -> "foreach"
            is Token.GlobalKeyword -> "global"
            is Token.Identifier -> v.value
            is Token.IfKeyword -> "if"
            is Token.IncludeKeyword -> "include"
            is Token.LetKeyword -> "let"
            is Token.LoopKeyword -> "loop"
            is Token.NamespaceKeyword -> "namespace"
            is Token.NewKeyword -> "new"
            else -> throw UnexpectedToken(
                "identifier-ish",
                v,
                v.span
            )
        }
    }
}