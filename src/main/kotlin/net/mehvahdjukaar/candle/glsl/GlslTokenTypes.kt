package net.mehvahdjukaar.candle.glsl

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class GlslTokenType(debugName: String) : IElementType(debugName, GlslLanguage)

object GlslTokenTypes {
    val LINE_COMMENT = GlslTokenType("LINE_COMMENT")
    val BLOCK_COMMENT = GlslTokenType("BLOCK_COMMENT")
    val DIRECTIVE = GlslTokenType("DIRECTIVE")
    val INCLUDE_PATH = GlslTokenType("INCLUDE_PATH")
    val KEYWORD = GlslTokenType("KEYWORD")
    val TYPE = GlslTokenType("TYPE")
    val BUILTIN_VARIABLE = GlslTokenType("BUILTIN_VARIABLE")
    val BUILTIN_FUNCTION = GlslTokenType("BUILTIN_FUNCTION")
    val FUNCTION_CALL = GlslTokenType("FUNCTION_CALL")
    val IDENTIFIER = GlslTokenType("IDENTIFIER")
    val NUMBER = GlslTokenType("NUMBER")
    val OPERATOR = GlslTokenType("OPERATOR")
    val LPAREN = GlslTokenType("(")
    val RPAREN = GlslTokenType(")")
    val LBRACE = GlslTokenType("{")
    val RBRACE = GlslTokenType("}")
    val LBRACKET = GlslTokenType("[")
    val RBRACKET = GlslTokenType("]")
    val SEMICOLON = GlslTokenType(";")
    val COMMA = GlslTokenType(",")
    val DOT = GlslTokenType(".")

    val COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)
    val STRINGS = TokenSet.create(INCLUDE_PATH)
}
