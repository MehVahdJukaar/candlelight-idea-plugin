package net.mehvahdjukaar.candle.glsl

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import net.mehvahdjukaar.candle.glsl.GlslTokenTypes as T

class GlslSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = GlslLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(KEYS_BY_TOKEN[tokenType])

    companion object {
        val LINE_COMMENT = createTextAttributesKey("CANDLE_GLSL_LINE_COMMENT", D.LINE_COMMENT)
        val BLOCK_COMMENT = createTextAttributesKey("CANDLE_GLSL_BLOCK_COMMENT", D.BLOCK_COMMENT)
        val DIRECTIVE = createTextAttributesKey("CANDLE_GLSL_DIRECTIVE", D.METADATA)
        val INCLUDE_PATH = createTextAttributesKey("CANDLE_GLSL_INCLUDE_PATH", D.STRING)
        val KEYWORD = createTextAttributesKey("CANDLE_GLSL_KEYWORD", D.KEYWORD)
        val QUALIFIER = createTextAttributesKey("CANDLE_GLSL_QUALIFIER", D.KEYWORD)
        val TYPE = createTextAttributesKey("CANDLE_GLSL_TYPE", D.KEYWORD)
        val BUILTIN_VARIABLE = createTextAttributesKey("CANDLE_GLSL_BUILTIN_VARIABLE", D.PREDEFINED_SYMBOL)
        val BUILTIN_FUNCTION = createTextAttributesKey("CANDLE_GLSL_BUILTIN_FUNCTION", D.STATIC_METHOD)
        val FUNCTION_CALL = createTextAttributesKey("CANDLE_GLSL_FUNCTION_CALL", D.FUNCTION_CALL)
        val IDENTIFIER = createTextAttributesKey("CANDLE_GLSL_IDENTIFIER", D.IDENTIFIER)
        val NUMBER = createTextAttributesKey("CANDLE_GLSL_NUMBER", D.NUMBER)
        val OPERATOR = createTextAttributesKey("CANDLE_GLSL_OPERATOR", D.OPERATION_SIGN)
        val PARENTHESES = createTextAttributesKey("CANDLE_GLSL_PARENTHESES", D.PARENTHESES)
        val BRACES = createTextAttributesKey("CANDLE_GLSL_BRACES", D.BRACES)
        val BRACKETS = createTextAttributesKey("CANDLE_GLSL_BRACKETS", D.BRACKETS)
        val SEMICOLON = createTextAttributesKey("CANDLE_GLSL_SEMICOLON", D.SEMICOLON)
        val COMMA = createTextAttributesKey("CANDLE_GLSL_COMMA", D.COMMA)
        val DOT = createTextAttributesKey("CANDLE_GLSL_DOT", D.DOT)

        //these come from GlslSymbolAnnotator, not the lexer
        val UNIFORM = createTextAttributesKey("CANDLE_GLSL_UNIFORM", D.STATIC_FIELD)
        val INPUT = createTextAttributesKey("CANDLE_GLSL_INPUT", D.INSTANCE_FIELD)
        val OUTPUT = createTextAttributesKey("CANDLE_GLSL_OUTPUT", D.INSTANCE_FIELD)
        val CONSTANT = createTextAttributesKey("CANDLE_GLSL_CONSTANT", D.CONSTANT)
        val GLOBAL_VARIABLE = createTextAttributesKey("CANDLE_GLSL_GLOBAL_VARIABLE", D.GLOBAL_VARIABLE)
        val LOCAL_VARIABLE = createTextAttributesKey("CANDLE_GLSL_LOCAL_VARIABLE", D.LOCAL_VARIABLE)
        val PARAMETER = createTextAttributesKey("CANDLE_GLSL_PARAMETER", D.PARAMETER)
        val MEMBER = createTextAttributesKey("CANDLE_GLSL_MEMBER", D.INSTANCE_FIELD)
        val STRUCT_NAME = createTextAttributesKey("CANDLE_GLSL_STRUCT_NAME", TYPE)
        val FUNCTION_DECLARATION = createTextAttributesKey("CANDLE_GLSL_FUNCTION_DECLARATION", D.FUNCTION_DECLARATION)
        val MACRO = createTextAttributesKey("CANDLE_GLSL_MACRO", D.CONSTANT)
        val LAYOUT_PARAM = createTextAttributesKey("CANDLE_GLSL_LAYOUT_PARAM", QUALIFIER)

        private val KEYS_BY_TOKEN: Map<IElementType, TextAttributesKey> = mapOf(
            T.LINE_COMMENT to LINE_COMMENT,
            T.BLOCK_COMMENT to BLOCK_COMMENT,
            T.DIRECTIVE to DIRECTIVE,
            T.INCLUDE_PATH to INCLUDE_PATH,
            T.KEYWORD to KEYWORD,
            T.QUALIFIER to QUALIFIER,
            T.TYPE to TYPE,
            T.BUILTIN_VARIABLE to BUILTIN_VARIABLE,
            T.BUILTIN_FUNCTION to BUILTIN_FUNCTION,
            T.FUNCTION_CALL to FUNCTION_CALL,
            T.IDENTIFIER to IDENTIFIER,
            T.NUMBER to NUMBER,
            T.OPERATOR to OPERATOR,
            T.LPAREN to PARENTHESES, T.RPAREN to PARENTHESES,
            T.LBRACE to BRACES, T.RBRACE to BRACES,
            T.LBRACKET to BRACKETS, T.RBRACKET to BRACKETS,
            T.SEMICOLON to SEMICOLON,
            T.COMMA to COMMA,
            T.DOT to DOT,
            TokenType.BAD_CHARACTER to HighlighterColors.BAD_CHARACTER,
        )
    }
}

class GlslSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        GlslSyntaxHighlighter()
}
