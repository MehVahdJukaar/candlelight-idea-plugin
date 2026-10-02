package net.mehvahdjukaar.candle.glsl

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon
import net.mehvahdjukaar.candle.glsl.GlslSyntaxHighlighter as H

class GlslColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = "GLSL"

    override fun getIcon(): Icon = GlslFileType.icon

    override fun getHighlighter(): SyntaxHighlighter = H()

    override fun getDemoText(): String = """
        #version 430
        #moj_import <minecraft:fog.glsl>

        /* flood fill step */
        layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;
        layout(rgba16f, binding = 0) uniform readonly image2D LightIn;

        uniform int Size;

        ivec2 texelOf(ivec3 slot) {
            return ivec2(slot.x % Size, slot.z / Size) * 2;
        }

        void main() {
            // skip the edges
            ivec3 local = ivec3(gl_GlobalInvocationID);
            vec4 light = imageLoad(LightIn, texelOf(local));
            float level = max(light.r, 1.5e-3);
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(
        AttributesDescriptor("Comments//Line comment", H.LINE_COMMENT),
        AttributesDescriptor("Comments//Block comment", H.BLOCK_COMMENT),
        AttributesDescriptor("Preprocessor directive", H.DIRECTIVE),
        AttributesDescriptor("Import path", H.INCLUDE_PATH),
        AttributesDescriptor("Keyword", H.KEYWORD),
        AttributesDescriptor("Type", H.TYPE),
        AttributesDescriptor("Built-in variable", H.BUILTIN_VARIABLE),
        AttributesDescriptor("Built-in function", H.BUILTIN_FUNCTION),
        AttributesDescriptor("Function call", H.FUNCTION_CALL),
        AttributesDescriptor("Identifier", H.IDENTIFIER),
        AttributesDescriptor("Number", H.NUMBER),
        AttributesDescriptor("Braces and Operators//Operator", H.OPERATOR),
        AttributesDescriptor("Braces and Operators//Parentheses", H.PARENTHESES),
        AttributesDescriptor("Braces and Operators//Braces", H.BRACES),
        AttributesDescriptor("Braces and Operators//Brackets", H.BRACKETS),
        AttributesDescriptor("Braces and Operators//Semicolon", H.SEMICOLON),
        AttributesDescriptor("Braces and Operators//Comma", H.COMMA),
        AttributesDescriptor("Braces and Operators//Dot", H.DOT),
    )

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}
