package net.mehvahdjukaar.candle.color

import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import java.awt.Color

/**
 * Shows the gutter color swatch (and picker) next to color-shaped literals in Java:
 *  - String literals like "#RRGGBB", "#AARRGGBB", "#RGB" (with or without the leading '#')
 *  - int/long hex literals with exactly 6 (RGB) or 8 (ARGB) digits, always
 *  - any other int (decimal or shorter hex) only when the surrounding name looks color-y
 *    (field/variable/assignment target, call parameter, enclosing setter method)
 */
class CandleColorProvider : ElementColorProvider {

    override fun getColorFrom(element: PsiElement): Color? {
        // Only fire on the leaf value token, so we don't paint duplicate swatches on parent nodes.
        if (element !is PsiJavaToken || element.firstChild != null) return null
        val literal = element.parent as? PsiLiteralExpression ?: return null

        when (val raw = literal.value) {
            is String -> return parseStringColor(raw)
            is Int -> return colorFromInt(literal, raw.toLong() and 0xFFFFFFFFL)
            is Long -> return colorFromInt(literal, raw)
            else -> return null
        }
    }

    override fun setColorTo(element: PsiElement, color: Color) {
        if (element !is PsiJavaToken) return
        val literal = element.parent as? PsiLiteralExpression ?: return
        val newText = when (literal.value) {
            is String -> rebuildString(literal.text, color)
            is Int, is Long -> rebuildInt(literal.text, color)
            else -> null
        } ?: return

        val factory = JavaPsiFacade.getElementFactory(literal.project)
        literal.replace(factory.createExpressionFromText(newText, literal))
    }

    // --- parsing ---------------------------------------------------------

    private fun parseStringColor(s: String): Color? {
        val hex = STRING_COLOR.matchEntire(s)?.groupValues?.get(1) ?: return null
        val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
        val v = full.toLong(16)
        return if (full.length == 8) Color(v.toInt(), true) else Color(v.toInt(), false)
    }

    private fun colorFromInt(literal: PsiLiteralExpression, value: Long): Color? {
        // Defer to the built-in JavaColorProvider for `new Color(...)` / `new JBColor(...)` args.
        if (isInsideColorConstructor(literal)) return null

        val text = literal.text.trim().trimEnd('L', 'l').replace("_", "")
        val isHex = text.startsWith("0x") || text.startsWith("0X")
        val hexDigits = if (isHex) text.substring(2).length else -1

        val shapeMatch = hexDigits == 6 || hexDigits == 8
        if (!shapeMatch && !isColorContext(literal)) return null

        // 8 hex digits, or a value with bits above 0xFFFFFF, carries alpha; otherwise opaque.
        val hasAlpha = hexDigits == 8 || (hexDigits != 6 && value > 0xFFFFFFL)
        return Color(value.toInt(), hasAlpha)
    }

    // --- rebuilding (preserve the original literal's shape) ---------------

    private fun rebuildString(original: String, color: Color): String {
        val inner = original.trim('"')
        val prefix = if (inner.startsWith("#")) "#" else ""
        val body = inner.removePrefix("#")
        val alpha = body.length == 8
        val upper = body.none { it in 'a'..'f' }
        return "\"" + prefix + packHex(color, alpha, upper) + "\""
    }

    private fun rebuildInt(original: String, color: Color): String {
        val suffix = original.takeLast(1).takeIf { it == "L" || it == "l" } ?: ""
        val core = original.trim().trimEnd('L', 'l').replace("_", "")
        val isHex = core.startsWith("0x") || core.startsWith("0X")

        if (isHex) {
            val digits = core.substring(2)
            val alpha = digits.length == 8
            val upper = digits.none { it in 'a'..'f' }
            val prefix = if (core.startsWith("0X")) "0X" else "0x"
            return prefix + packHex(color, alpha, upper) + suffix
        }

        // Decimal: keep it decimal when the packed value is a non-negative int (or was a long).
        val alpha = color.alpha != 255
        val packed = if (alpha) color.rgb.toLong() and 0xFFFFFFFFL else (color.rgb.toLong() and 0xFFFFFFL)
        return if (suffix.isNotEmpty() || packed <= Int.MAX_VALUE) {
            packed.toString() + suffix
        } else {
            "0x" + packHex(color, alpha = true, upper = true)
        }
    }

    private fun packHex(color: Color, alpha: Boolean, upper: Boolean): String {
        val v = if (alpha) color.rgb.toLong() and 0xFFFFFFFFL else color.rgb.toLong() and 0xFFFFFFL
        val s = v.toString(16).padStart(if (alpha) 8 else 6, '0')
        return if (upper) s.uppercase() else s
    }

    // --- name-based context detection ------------------------------------

    private fun isColorContext(literal: PsiLiteralExpression): Boolean {
        val expr = outermostExpression(literal)
        val parent = expr.parent

        (parent as? PsiVariable)?.name?.let { if (looksColory(it)) return true }

        (parent as? PsiAssignmentExpression)?.let { asg ->
            if (asg.rExpression === expr) {
                (asg.lExpression as? PsiReferenceExpression)?.referenceName?.let {
                    if (looksColory(it)) return true
                }
            }
        }

        (parent as? PsiExpressionList)?.let { args ->
            val idx = args.expressions.indexOf(expr)
            when (val call = args.parent) {
                is PsiMethodCallExpression -> {
                    call.methodExpression.referenceName?.let { if (looksColory(it)) return true }
                    call.resolveMethod()?.parameterList?.parameters?.getOrNull(idx)?.name
                        ?.let { if (looksColory(it)) return true }
                }
                is PsiNewExpression -> call.resolveConstructor()?.parameterList?.parameters
                    ?.getOrNull(idx)?.name?.let { if (looksColory(it)) return true }
            }
        }

        if (parent is PsiReturnStatement) {
            PsiTreeUtil.getParentOfType(parent, com.intellij.psi.PsiMethod::class.java)
                ?.name?.let { if (looksColory(it)) return true }
        }

        return false
    }

    private fun isInsideColorConstructor(literal: PsiLiteralExpression): Boolean {
        val args = outermostExpression(literal).parent as? PsiExpressionList ?: return false
        val newExpr = args.parent as? PsiNewExpression ?: return false
        val name = newExpr.classReference?.referenceName ?: return false
        return name == "Color" || name == "JBColor"
    }

    private fun outermostExpression(literal: PsiLiteralExpression): PsiExpression {
        var cur: PsiExpression = literal
        while (true) {
            val p = cur.parent
            cur = if (p is PsiParenthesizedExpression || p is PsiTypeCastExpression) p else return cur
        }
    }

    private fun looksColory(name: String) = COLOR_WORDS.any { name.contains(it, ignoreCase = true) }

    companion object {
        private val STRING_COLOR =
            Regex("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
        private val COLOR_WORDS = listOf("color", "colour", "tint", "rgb", "argb", "hex")
    }
}
