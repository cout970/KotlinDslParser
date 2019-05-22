data class Input(val tokens: List<Token>, var pos: Int) {
    val current: Token get() = tokens[pos]
    val next: Token? get() = tokens.getOrNull(pos + 1)

    fun <T> restore(newPos: Int): T? {
        pos = newPos
        return null
    }

    fun readTokenType(type: TokenType) {
        if (current.type != type) {
            throw ParseError(
                "Expected token of type $type, but found ${current.type}",
                current.start to current.end
            )
        }
        pos++
    }

    fun readId(): String {
        val current = current
        if (current !is Token.TokenId) {
            throw ParseError(
                "Expected token of type IDENTIFIER, but found ${current.type}",
                current.start to current.end
            )
        }
        pos++
        return current.name
    }

    fun readKeyword(key: String) {
        val current = current
        if (current !is Token.TokenId) {
            throw ParseError(
                "Expected token of type IDENTIFIER, but found ${current.type}",
                current.start to current.end
            )
        }

        if (current.name != key) {
            throw ParseError(
                "Expected keyword $key, but found '${current.name}'",
                current.start to current.end
            )
        }

        pos++
    }

    fun <T> tryParser(parser: (Input) -> T): T? {
        val start = pos
        return try {
            parser(this)
        } catch (e: ParseError) {
            pos = start
            null
        }
    }
}

private class ParseError(msg: String, val pos: Pair<Int, Int>) : RuntimeException(msg)

object Parser {

    fun parseFile(code: String): List<Function> {
        val tokens = Tokenizer.readAll(code)
        val input = Input(tokens, 0)
        val functions = mutableListOf<Function>()

        try {
            while (input.pos < input.tokens.size) {
                functions += parseFunction(input)
            }
        } catch (e: ParseError) {

            val line = code.substring(0, e.pos.first).count { it == '\n' } + 1
            val column = e.pos.first - code.substring(0, e.pos.first).lastIndexOf('\n')

            println("${e.message}\n at $line:$column '${code.substring(e.pos.first, e.pos.second)}'")
            e.printStackTrace()
            error("Parse error")
        }

        return functions
    }

    private fun parseFunction(input: Input): Function {
        val header = parseFunctionHeader(input)
        return if (header.external) {
            Function(header, emptyList())
        } else {
            Function(header, parseFunctionBody(input))
        }
    }

    private fun parseFunctionHeader(input: Input): FunctionHeader {
        val external = input.tryParser { it.readKeyword("external") } != null
        input.tryParser { it.readKeyword("operator") }

        input.readKeyword("fun")

        val receiver = input.tryParser { input.readId().apply { input.readTokenType(TokenType.DOT) } } ?: null
        val name = input.readId()

        input.readTokenType(TokenType.LEFT_PAREN)
        val arguments = mutableListOf<FunctionArgument>()
        while (input.current.type != TokenType.RIGHT_PAREN) {
            arguments += parseArgument(input)
            if (input.current.type != TokenType.COMMA) break
            input.readTokenType(TokenType.COMMA)
        }
        input.readTokenType(TokenType.RIGHT_PAREN)

        val returnType = input.tryParser { it.readTokenType(TokenType.COLON); it.readId() }

        return FunctionHeader(external, receiver, name, arguments, returnType)
    }

    private fun parseArgument(input: Input): FunctionArgument {
        val name = input.readId()
        input.readTokenType(TokenType.COLON)
        val type = parseType(input)
        return FunctionArgument(name, type)
    }

    private fun parseFunctionBody(input: Input): List<DslNode> {
        val body = mutableListOf<DslNode>()

        input.readTokenType(TokenType.LEFT_BRACE)
        while (input.current.type != TokenType.RIGHT_BRACE) {
            body += parseDslNode(input)
        }
        input.readTokenType(TokenType.RIGHT_BRACE)

        return body
    }

    private fun parseDslNode(input: Input): DslNode {
        val current = input.current
        return if (input.current is Token.TokenId) {
            if (input.next?.type == TokenType.EQUALS) {
                parseAssignment(input)
            } else {
                parseFunctionCall(input)
            }
        } else {
            when (current.type) {
                TokenType.MINUS, TokenType.PLUS -> {
                    parseUnaryOperator(input)
                }
                else -> throw ParseError("Expected unary operator, found: $current", current.start to current.end)
            }
        }
    }

    private fun parseAssignment(input: Input): DslNode {
        val variable = input.readId()
        input.readTokenType(TokenType.EQUALS)
        val value = parseValue(input)
        return DslNode.Assignment(variable, value)
    }

    private fun parseUnaryOperator(input: Input): DslNode {
        val operator = if (input.current.type == TokenType.PLUS) "+" else "-"
        input.pos++

        val value = parseValue(input)

        return DslNode.UnaryOperator(operator, value)
    }

    private fun parseFunctionCall(input: Input): DslNode.FunctionCall {
        val receiver = input.tryParser {
            it.readId().apply { it.readTokenType(TokenType.DOT) }
        }

        val name = input.readId()

        val parameters = if (input.current.type == TokenType.LEFT_PAREN) {
            val list = mutableListOf<Parameter>()

            input.readTokenType(TokenType.LEFT_PAREN)
            while (input.current.type != TokenType.RIGHT_PAREN) {
                list += parseParameter(input)
            }
            input.readTokenType(TokenType.RIGHT_PAREN)

            list
        } else listOf<Parameter>()

        val children = if (input.current.type == TokenType.LEFT_BRACE) {
            parseFunctionBody(input)
        } else listOf()

        return DslNode.FunctionCall(receiver, name, parameters, children)
    }

    private fun parseParameter(input: Input): Parameter {
        return Parameter.Single(parseValue(input))
    }

    private fun parseValue(input: Input): Value {
        val current = input.current
        when (current) {
            is Token.TokenString -> {
                input.pos++
                return Value.StringValue(current.content)
            }
            is Token.TokenNumber -> {
                input.pos++
                return Value.NumberValue(current.value)
            }
            is Token.TokenId -> {
                return if (input.next?.type == TokenType.DOT) {
                    input.pos += 2
                    Value.EnumValue(current.name, input.readId())
                } else {
                    Value.FunctionValue(parseFunctionCall(input))
                }
            }
        }
        throw ParseError("Expected value, found: $current", current.start to current.end)
    }

    private fun parseType(input: Input): String {

        val current = input.current

        if (current.type == TokenType.LEFT_PAREN) {
            // (Unit) -> Unit
            // (Unit, Unit) -> Unit
            input.readTokenType(TokenType.LEFT_PAREN)
            val args = mutableListOf<String>()
            while (input.current.type != TokenType.RIGHT_PAREN) {
                args += parseType(input)
            }
            input.readTokenType(TokenType.RIGHT_PAREN)
            input.readTokenType(TokenType.ARROW)
            val ret = parseType(input)

            return "(${args.joinToString(", ")}) -> $ret"
        } else if (current is Token.TokenId) {
            if (input.next?.type == TokenType.DOT) {
                // Unit.(Unit) -> Unit
                val receiver = current.name
                input.pos++
                input.readTokenType(TokenType.DOT)
                input.readTokenType(TokenType.LEFT_PAREN)
                val args = mutableListOf<String>()
                while (input.current.type != TokenType.RIGHT_PAREN) {
                    args += parseType(input)
                }
                input.readTokenType(TokenType.RIGHT_PAREN)
                input.readTokenType(TokenType.ARROW)
                val ret = parseType(input)

                return "$receiver.(${args.joinToString(", ")}) -> $ret"
            } else {
                // Unit
                input.pos++
                return current.name
            }
        } else {
            throw ParseError("Expected type found: $current", current.start to current.end)
        }
    }
}