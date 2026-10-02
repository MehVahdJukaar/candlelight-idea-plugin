package net.mehvahdjukaar.candle.color

import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import java.awt.Color

class CandleColorProvider : ElementColorProvider {

    override fun getColorFrom(element: PsiElement): Color? {
        // leaf token only or parent nodes get duplicate swatches
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

    private fun parseStringColor(s: String): Color? {
        val hex = STRING_COLOR.matchEntire(s)?.groupValues?.get(1) ?: return null
        val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
        val v = full.toLong(16)
        return if (full.length == 8) Color(v.toInt(), true) else Color(v.toInt(), false)
    }

    private fun colorFromInt(literal: PsiLiteralExpression, value: Long): Color? {
        //JavaColorProvider already does these
        if (isInsideColorConstructor(literal)) return null

        val text = literal.text.trim().trimEnd('L', 'l').replace("_", "")
        val isHex = text.startsWith("0x") || text.startsWith("0X")
        val hexDigits = if (isHex) text.substring(2).length else -1

        val shapeMatch = hexDigits == 6 || hexDigits == 8
        if (!shapeMatch && !isColorContext(literal)) return null

        val hasAlpha = hexDigits == 8 || (hexDigits != 6 && value > 0xFFFFFFL)
        return Color(value.toInt(), hasAlpha)
    }

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
            val i = args.expressions.indexOf(expr)
            when (val call = args.parent) {
                is PsiMethodCallExpression -> {
                    call.methodExpression.referenceName?.let { if (looksColory(it)) return true }
                    call.resolveMethod()?.parameterList?.parameters?.getOrNull(i)?.name
                        ?.let { if (looksColory(it)) return true }
                }
                is PsiNewExpression -> call.resolveConstructor()?.parameterList?.parameters
                    ?.getOrNull(i)?.name?.let { if (looksColory(it)) return true }
            }
        }

        if (parent is PsiReturnStatement) {
            PsiTreeUtil.getParentOfType(parent, PsiMethod::class.java)
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
