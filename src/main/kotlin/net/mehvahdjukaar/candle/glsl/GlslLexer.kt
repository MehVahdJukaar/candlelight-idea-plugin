package net.mehvahdjukaar.candle.glsl

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import net.mehvahdjukaar.candle.glsl.GlslTokenTypes as T

class GlslLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null
    private var tokenState = 0
    private var state = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEnd = endOffset
        tokenEnd = startOffset
        state = initialState
        advance()
    }

    override fun getState(): Int = tokenState

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd
        tokenState = state
        if (tokenStart >= bufferEnd) {
            tokenType = null
            return
        }
        tokenType = lexToken(buffer[tokenStart])
    }

    private fun at(offset: Int): Char = if (offset < bufferEnd) buffer[offset] else '\u0000'

    private fun lexToken(c: Char): IElementType {
        var i = tokenStart
        when {
            c.isWhitespace() -> {
                while (i < bufferEnd && buffer[i].isWhitespace()) {
                    if (buffer[i] == '\n') state = 0
                    i++
                }
                tokenEnd = i
                return TokenType.WHITE_SPACE
            }
            c == '/' && at(i + 1) == '/' -> {
                while (i < bufferEnd && buffer[i] != '\n') i++
                tokenEnd = i
                return T.LINE_COMMENT
            }
            c == '/' && at(i + 1) == '*' -> {
                i += 2
                while (i < bufferEnd && !(buffer[i] == '*' && at(i + 1) == '/')) i++
                tokenEnd = minOf(i + 2, bufferEnd)
                return T.BLOCK_COMMENT
            }
            c == '#' -> {
                i++
                while (at(i) == ' ' || at(i) == '\t') i++
                val nameStart = i
                while (at(i).isLetterOrDigit() || at(i) == '_') i++
                val directive = buffer.subSequence(nameStart, i).toString()
                if (directive == "include" || directive == "moj_import") state = STATE_INCLUDE
                tokenEnd = i
                return T.DIRECTIVE
            }
            state == STATE_INCLUDE && (c == '<' || c == '"') -> {
                val close = if (c == '<') '>' else '"'
                i++
                while (i < bufferEnd && buffer[i] != close && buffer[i] != '\n') i++
                if (at(i) == close) i++
                tokenEnd = i
                state = 0
                return T.INCLUDE_PATH
            }
            c.isDigit() || (c == '.' && at(i + 1).isDigit()) -> {
                tokenEnd = numberEnd(i)
                return T.NUMBER
            }
            c.isLetter() || c == '_' -> {
                while (at(i).isLetterOrDigit() || at(i) == '_') i++
                tokenEnd = i
                return identifierType(buffer.subSequence(tokenStart, i).toString())
            }
        }
        tokenEnd = i + 1
        return when (c) {
            '(' -> T.LPAREN
            ')' -> T.RPAREN
            '{' -> T.LBRACE
            '}' -> T.RBRACE
            '[' -> T.LBRACKET
            ']' -> T.RBRACKET
            ';' -> T.SEMICOLON
            ',' -> T.COMMA
            '.' -> T.DOT
            else -> {
                if (c !in OPERATOR_CHARS) return TokenType.BAD_CHARACTER
                //stop before a comment glued to the operator, like a=b//foo
                while (at(i + 1) in OPERATOR_CHARS && !(at(i + 1) == '/' && (at(i + 2) == '/' || at(i + 2) == '*'))) i++
                tokenEnd = i + 1
                T.OPERATOR
            }
        }
    }

    private fun numberEnd(start: Int): Int {
        var i = start
        if (at(i) == '0' && (at(i + 1) == 'x' || at(i + 1) == 'X')) {
            i += 2
            while (at(i).isLetterOrDigit()) i++
            return i
        }
        while (at(i).isDigit() || at(i) == '.') i++
        if (at(i) == 'e' || at(i) == 'E') {
            i++
            if (at(i) == '+' || at(i) == '-') i++
            while (at(i).isDigit()) i++
        }
        // u, f, lf, hf suffixes
        while (at(i).isLetter()) i++
        return i
    }

    private fun identifierType(word: String): IElementType {
        if (word in GlslWords.TYPES) return T.TYPE
        if (word in GlslWords.KEYWORDS) return T.KEYWORD
        if (word in GlslWords.QUALIFIERS) return T.QUALIFIER
        if (word.startsWith("gl_")) return T.BUILTIN_VARIABLE

        var i = tokenEnd
        while (at(i) == ' ' || at(i) == '\t') i++
        if (at(i) != '(') return T.IDENTIFIER
        return if (word in GlslWords.BUILTIN_FUNCTIONS) T.BUILTIN_FUNCTION else T.FUNCTION_CALL
    }

    companion object {
        private const val STATE_INCLUDE = 1
        private const val OPERATOR_CHARS = "+-*/%=<>!&|^~?:"
    }
}
