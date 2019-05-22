enum class TokenType {
    IDENTIFIER,
    NUMBER,
    STRING,

    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_BRACE,
    RIGHT_BRACE,

    DOT,
    COMMA,
    COLON,
    PLUS,
    MINUS,
    EQUALS,
    SEMICOLON,
    ARROW,
}

sealed class Token(open val type: TokenType, open val start: Int, open val end: Int) {
    data class TokenId(
        val name: String, override val start: Int, override val end: Int
    ) : Token(TokenType.IDENTIFIER, start, end)

    data class TokenString(
        val content: String, override val start: Int, override val end: Int
    ) : Token(TokenType.STRING, start, end)

    data class TokenNumber(
        val value: Number, override val start: Int, override val end: Int
    ) : Token(TokenType.NUMBER, start, end)

    data class TokenChar(
        override val type: TokenType, override val start: Int, override val end: Int
    ) : Token(type, start, end)
}

object Tokenizer {
    fun readAll(code: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = 0

        loop@ while (pos < code.length) {
            val char = code[pos]
            val nextChar = code.getOrNull(pos + 1) ?: 0.toChar()

            // Remove spaces
            if (char == ' ' || char == '\n') {
                pos++
                continue@loop
            }

            // Remove coments
            if (char == '/' && nextChar == '/') {
                while (pos < code.length && code[pos] != '\n') {
                    pos++
                }
                continue@loop
            }

            // Check simple characters
            val type = when (char) {
                '(' -> TokenType.LEFT_PAREN
                ')' -> TokenType.RIGHT_PAREN
                '{' -> TokenType.LEFT_BRACE
                '}' -> TokenType.RIGHT_BRACE
                ',' -> TokenType.COMMA
                ':' -> TokenType.COLON
                '+' -> TokenType.PLUS
                '=' -> TokenType.EQUALS
                ';' -> TokenType.SEMICOLON
                else -> {
                    // Check complex tokens
                    when {
                        char == '-' && nextChar == '>' -> {
                            tokens += Token.TokenChar(TokenType.ARROW, pos, pos + 1)
                            pos += 2
                        }
                        char == '-' -> {
                            tokens += Token.TokenChar(TokenType.MINUS, pos, pos + 1)
                            pos++
                        }
                        (char == '.' && nextChar.isDigit()) || char.isDigit() -> {
                            val (num, end) = parseNumber(code, pos)
                            tokens += Token.TokenNumber(num, pos, end)
                            pos = end
                        }
                        char == '.' -> {
                            tokens += Token.TokenChar(TokenType.DOT, pos, pos + 1)
                            pos++
                        }
                        char == '"' -> {
                            val (name, end) = parseString(code, pos)
                            tokens += Token.TokenString(name, pos, end)
                            pos = end
                        }
                        char.isJavaIdentifierStart() -> {
                            val (name, end) = parseIdentifier(code, pos)
                            tokens += Token.TokenId(name, pos, end)
                            pos = end
                        }
                        else -> error("Unknown char '$char'")
                    }
                    continue@loop
                }
            }

            // Add simple char to tokens
            tokens += Token.TokenChar(type, pos, pos + 1)
            pos++
        }

        return tokens
    }

    private fun parseNumber(code: String, start: Int): Pair<Number, Int> {
        var pos = start

        if (code[pos] == '.') {
            pos++
        }

        while (pos < code.length && code[pos].isDigit()) {
            pos++
        }

        val num = code.substring(start, pos).toDouble()

        return num to pos
    }

    private fun parseIdentifier(code: String, start: Int): Pair<String, Int> {
        var pos = start + 1

        while (pos < code.length && code[pos].isJavaIdentifierPart()) {
            pos++
        }

        return code.substring(start, pos) to pos
    }

    private fun parseString(code: String, start: Int): Pair<String, Int> {
        var pos = start + 1

        while (pos < code.length && code[pos] != '"') {
            pos++
        }

        if (code[pos] != '"') {
            error("Unfinished string at $pos")
        }

        pos++

        return code.substring(start, pos) to pos
    }

}

