package net.mehvahdjukaar.candle.glsl

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import net.mehvahdjukaar.candle.glsl.GlslSyntaxHighlighter as H
import net.mehvahdjukaar.candle.glsl.GlslTokenTypes as T

class GlslSymbolAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is GlslFile) return
        SymbolScan(holder).run(element.node.getChildren(null))
    }
}

// one pass over the flat tokens, guesses declarations from "type name" pairs. good enough for shaders
private class SymbolScan(private val holder: AnnotationHolder) {

    private class Token(val node: ASTNode, val startsLine: Boolean) {
        val type get() = node.elementType
        val text: String = node.text
    }

    private val globals = HashMap<String, TextAttributesKey>()
    private val locals = HashMap<String, TextAttributesKey>()
    private val structTypes = HashSet<String>()
    private val macros = HashSet<String>()

    private var braceDepth = 0
    private var parenDepth = 0
    private var qualifierKind: TextAttributesKey? = null
    private var declKind: TextAttributesKey? = null
    private var expectingName = false
    private var declParenDepth = 0
    private var paramsParenDepth = -1
    private var layoutParenDepth = -1
    private var structPending = false
    private var pendingBlockKind: TextAttributesKey? = null
    private var blockKind: TextAttributesKey? = null
    private var blockDepth = -1
    private var prev: Token? = null

    fun run(nodes: Array<ASTNode>) {
        val tokens = ArrayList<Token>()
        var newLine = true
        for (node in nodes) {
            val type = node.elementType
            if (type == TokenType.WHITE_SPACE) {
                if ('\n' in node.text) newLine = true
                continue
            }
            if (type == T.LINE_COMMENT || type == T.BLOCK_COMMENT) continue
            tokens.add(Token(node, newLine))
            newLine = false
        }

        var i = 0
        while (i < tokens.size) {
            if (tokens[i].type == T.DIRECTIVE) {
                i = scanDirective(tokens, i)
                continue
            }
            scan(tokens[i], tokens.getOrNull(i + 1))
            prev = tokens[i]
            i++
        }
    }

    private fun mark(token: Token, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(token.node).textAttributes(key).create()
    }

    private fun scanDirective(tokens: List<Token>, start: Int): Int {
        val directive = tokens[start].text.drop(1).trim()
        val definesName = directive == "define" || directive == "undef" || directive == "ifdef" || directive == "ifndef"
        var i = start + 1
        while (i < tokens.size && !tokens[i].startsLine) {
            val token = tokens[i]
            val isName = token.type == T.IDENTIFIER || token.type == T.FUNCTION_CALL
            if (isName && definesName && i == start + 1) {
                macros.add(token.text)
                mark(token, H.MACRO)
            } else if (isName && token.text in macros) {
                mark(token, H.MACRO)
            }
            i++
        }
        return i
    }

    private fun isTypeName(token: Token) = token.type == T.TYPE || (token.type == T.IDENTIFIER && token.text in structTypes)

    private fun currentDeclKind(): TextAttributesKey = when {
        paramsParenDepth != -1 && parenDepth == paramsParenDepth -> H.PARAMETER
        blockDepth != -1 && braceDepth == blockDepth -> blockKind ?: H.MEMBER
        braceDepth > 0 -> H.LOCAL_VARIABLE
        else -> qualifierKind ?: H.GLOBAL_VARIABLE
    }

    private fun declare(token: Token, kind: TextAttributesKey) {
        when (kind) {
            H.LOCAL_VARIABLE, H.PARAMETER -> locals[token.text] = kind
            H.MEMBER -> {}
            else -> globals[token.text] = kind
        }
        mark(token, kind)
    }

    private fun scan(token: Token, next: Token?) {
        when (token.type) {
            T.LBRACE -> {
                braceDepth++
                if (pendingBlockKind != null) {
                    blockKind = pendingBlockKind
                    blockDepth = braceDepth
                    pendingBlockKind = null
                }
                declKind = null
                expectingName = false
                qualifierKind = null
            }

            T.RBRACE -> {
                if (braceDepth == blockDepth) {
                    //instance name after the block, like "} foo;"
                    declKind = if (blockKind == H.MEMBER) H.GLOBAL_VARIABLE else blockKind
                    expectingName = true
                    declParenDepth = parenDepth
                    blockDepth = -1
                    blockKind = null
                }
                braceDepth--
                if (braceDepth == 0 && declKind == null) locals.clear()
            }

            T.LPAREN -> {
                parenDepth++
                if (prev?.text == "layout") layoutParenDepth = parenDepth
                expectingName = false
            }
            T.RPAREN -> {
                if (parenDepth == layoutParenDepth) layoutParenDepth = -1
                if (parenDepth == paramsParenDepth) paramsParenDepth = -1
                parenDepth--
            }

            T.SEMICOLON -> {
                declKind = null
                expectingName = false
                qualifierKind = null
                structPending = false
                pendingBlockKind = null
                if (braceDepth == 0) locals.clear()
            }

            T.COMMA -> if (declKind != null && parenDepth == declParenDepth) expectingName = true

            T.QUALIFIER -> {
                val kind = when (token.text) {
                    "uniform", "buffer" -> H.UNIFORM
                    "in", "attribute", "varying" -> H.INPUT
                    "out" -> H.OUTPUT
                    "const" -> H.CONSTANT
                    else -> null
                }
                if (kind != null && (qualifierKind == null || qualifierKind == H.CONSTANT)) qualifierKind = kind
            }

            T.KEYWORD -> if (token.text == "struct") structPending = true

            T.TYPE -> if (next?.type != T.LPAREN) startDeclaration()

            T.IDENTIFIER -> scanIdentifier(token, next)

            T.FUNCTION_CALL -> {
                if (token.text in macros) {
                    mark(token, H.MACRO)
                } else if (expectingName && braceDepth == 0) {
                    mark(token, H.FUNCTION_DECLARATION)
                    paramsParenDepth = parenDepth + 1
                    declKind = null
                    expectingName = false
                    qualifierKind = null
                    locals.clear()
                }
            }
        }
    }

    private fun startDeclaration() {
        declKind = currentDeclKind()
        expectingName = true
        declParenDepth = parenDepth
    }

    private fun scanIdentifier(token: Token, next: Token?) {
        val name = token.text
        when {
            layoutParenDepth != -1 -> mark(token, H.LAYOUT_PARAM)
            prev?.type == T.DOT -> mark(token, H.MEMBER)
            structPending -> {
                structTypes.add(name)
                mark(token, H.STRUCT_NAME)
                structPending = false
                pendingBlockKind = H.MEMBER
            }
            expectingName && declKind != null -> {
                declare(token, declKind!!)
                expectingName = false
            }
            //interface block like "uniform Globals { ... }"
            braceDepth == 0 && qualifierKind != null && next?.type == T.LBRACE -> {
                pendingBlockKind = qualifierKind
                mark(token, H.STRUCT_NAME)
            }

            name in structTypes -> {
                mark(token, H.STRUCT_NAME)
                if (next?.type != T.LPAREN) startDeclaration()
            }

            else -> {
                val kind = locals[name] ?: globals[name] ?: if (name in macros) H.MACRO else null
                if (kind != null) mark(token, kind)
            }
        }
    }
}
