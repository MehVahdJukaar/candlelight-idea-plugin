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
        #define <macro>MAX_LEVEL</macro> 15.0

        /* flood fill step */
        layout(<lp>local_size_x</lp> = 4, <lp>local_size_y</lp> = 4, <lp>local_size_z</lp> = 4) in;
        layout(<lp>rgba16f</lp>, <lp>binding</lp> = 0) uniform readonly image2D <uni>LightIn</uni>;

        layout(<lp>std140</lp>) uniform <struct>Globals</struct> {
            int <uni>Size</uni>;
        };

        struct <struct>Cell</struct> {
            vec3 <member>light</member>;
        };

        in vec2 <in>texCoord</in>;
        out vec4 <out>fragColor</out>;
        const float <const>FALLOFF</const> = 1.0;
        float <global>totalLight</global>;

        ivec2 <fdecl>texelOf</fdecl>(ivec3 <param>slot</param>) {
            return ivec2(<param>slot</param>.<member>x</member> % <uni>Size</uni>, <param>slot</param>.<member>z</member> / <uni>Size</uni>) * 2;
        }

        void <fdecl>main</fdecl>() {
            // skip the edges
            ivec3 <local>local</local> = ivec3(gl_GlobalInvocationID);
            if (<local>local</local>.<member>x</member> >= <uni>Size</uni>) return;
            <struct>Cell</struct> <local>cell</local>;
            <local>cell</local>.<member>light</member> = imageLoad(<uni>LightIn</uni>, texelOf(<local>local</local>)).<member>rgb</member>;
            float <local>level</local> = max(<local>cell</local>.<member>light</member>.<member>r</member> * <macro>MAX_LEVEL</macro> - <const>FALLOFF</const>, 1.5e-3);
            <out>fragColor</out> = vec4(<in>texCoord</in>, <local>level</local>, 1.0);
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "uni" to H.UNIFORM,
        "in" to H.INPUT,
        "out" to H.OUTPUT,
        "const" to H.CONSTANT,
        "global" to H.GLOBAL_VARIABLE,
        "local" to H.LOCAL_VARIABLE,
        "param" to H.PARAMETER,
        "member" to H.MEMBER,
        "struct" to H.STRUCT_NAME,
        "fdecl" to H.FUNCTION_DECLARATION,
        "macro" to H.MACRO,
        "lp" to H.LAYOUT_PARAM,
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(
        AttributesDescriptor("Comments//Line comment", H.LINE_COMMENT),
        AttributesDescriptor("Comments//Block comment", H.BLOCK_COMMENT),
        AttributesDescriptor("Preprocessor directive", H.DIRECTIVE),
        AttributesDescriptor("Import path", H.INCLUDE_PATH),
        AttributesDescriptor("Keyword", H.KEYWORD),
        AttributesDescriptor("Qualifier", H.QUALIFIER),
        AttributesDescriptor("Type", H.TYPE),
        AttributesDescriptor("Built-in variable", H.BUILTIN_VARIABLE),
        AttributesDescriptor("Built-in function", H.BUILTIN_FUNCTION),
        AttributesDescriptor("Function call", H.FUNCTION_CALL),
        AttributesDescriptor("Function declaration", H.FUNCTION_DECLARATION),
        AttributesDescriptor("Struct or block name", H.STRUCT_NAME),
        AttributesDescriptor("Macro", H.MACRO),
        AttributesDescriptor("Layout parameter", H.LAYOUT_PARAM),
        AttributesDescriptor("Variables//Uniform or buffer", H.UNIFORM),
        AttributesDescriptor("Variables//Input (in, attribute)", H.INPUT),
        AttributesDescriptor("Variables//Output (out)", H.OUTPUT),
        AttributesDescriptor("Variables//Global constant", H.CONSTANT),
        AttributesDescriptor("Variables//Global variable", H.GLOBAL_VARIABLE),
        AttributesDescriptor("Variables//Local variable", H.LOCAL_VARIABLE),
        AttributesDescriptor("Variables//Parameter", H.PARAMETER),
        AttributesDescriptor("Variables//Member or swizzle", H.MEMBER),
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
