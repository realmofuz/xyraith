package langimpl.lang.parser

import langimpl.error.UnexpectedEOF
import langimpl.error.UnexpectedToken
import langimpl.lang.lexer.Token
import parser.Ast
import parser.CommandArgument
import parser.PathName
import parser.Type
import java.lang.IndexOutOfBoundsException
import java.sql.SQLWarning
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
        println("next returned: $n")
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
        println("peek returned: $n")
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
        println("Parsing class {")
        val annotations: MutableList<PathName> = mutableListOf()
        while(true) {
            annotations.add(parseAnnotation()?.name ?: break)
        }

        val keyword = next()
        println("KEYWORD: ${keyword}")
        if(keyword is Token.ClassKeyword || keyword is Token.NamespaceKeyword) {
            val isStaticClass = keyword is Token.NamespaceKeyword
            val name = parsePathName()
            val extends: Type.Object = if(peek() is Token.Colon) {
                expect<Token.Colon>("colon")
                parseType() as Type.Object
            } else Type.Object(
                PathName.parse("java.lang.Object"),
                listOf(),
                false
            )
            val headers = mutableListOf<Ast.Header>()
            expect<Token.OpenBrace>("open braces")
            while(true) {
                if(peek() is Token.CloseBrace)
                    break
                val header = parseHeader()
                println("Header: $header")
                headers.add(header)
            }
            expect<Token.CloseBrace>("close braces")

            return Ast.Class(
                name,
                listOf(),
                headers,
                isStaticClass,
                extends,
                annotations.contains(PathName.parse("native"))
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
            "string" -> Type.String
            "void", "auto" -> Type.Void
            "array" -> Type.Array(generics[0])
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
        println("Parsing header")
        when(val keyword = peek()) {
            is Token.At -> {
                return parseAnnotation()!!
            }
            is Token.LetKeyword -> {
                val letKeyword = expect<Token.LetKeyword>("let keyword")
                val fieldName = expect<Token.Identifier>("identifier")
                val colon = expect<Token.Colon>("colon")
                val type = parseType()
                val equals = expect<Token.Equals>("equals")
                val value = parseValue()
                return Ast.DeclareField(
                    fieldName.value,
                    value,
                    fieldName.span,
                    type,
                )
            }
            is Token.CommandKeyword -> {
                println("Parsing new command")
                expect<Token.CommandKeyword>("command keyword")
                val name = expect<Token.Identifier>("command name")
                val arguments = mutableMapOf<String, Type>()
                while(true) {
                    if(peek() is Token.Arrow)
                        break
                    val argumentName = expect<Token.Identifier>("name")
                    val colon = expect<Token.Colon>("colon")
                    val type = parseType()
                    arguments[argumentName.value] = type
                }
                expect<Token.Arrow>("arrow")
                val returnType = parseType()

                return Ast.Function(
                    PathName.parse(name.value),
                    parseBlock(),
                    name.span,
                    arguments,
                    returnType
                )
            }
            is Token.EventKeyword -> TODO()
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
                    parseAccess()
                } else {
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
                println("declaring variable")
                return Ast.DeclareVariable(
                    name.value,
                    value,
                    name.span,
                    type
                )

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
            is Token.Number -> Ast.Number(next.value)
            is Token.OpenParen -> parseAccess()
            is Token.ArrayOfKeyword -> TODO()
           else -> throw UnexpectedToken("valid value", next, next.span)
        }
    }

    /*
    Parse a path name.
    e.g x.y.z -> PathName(["x", "y", "z"])
     */
    fun parsePathName(pathName: PathName = PathName(mutableListOf())): PathName {
        val newIdentifier = expect<Token.Identifier>("identifier")
        if(peek() is Token.Dot) {
            expect<Token.Dot>("period")
            return parsePathName(PathName((pathName.path + newIdentifier.value).toMutableList()))
        }
        return PathName((pathName.path + newIdentifier.value).toMutableList())
    }
}