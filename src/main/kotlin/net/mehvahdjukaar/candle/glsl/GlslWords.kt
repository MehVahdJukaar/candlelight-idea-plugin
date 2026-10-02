package net.mehvahdjukaar.candle.glsl

object GlslWords {

    val KEYWORDS = setOf(
        "attribute", "const", "uniform", "varying", "buffer", "shared", "coherent", "volatile", "restrict",
        "readonly", "writeonly", "layout", "centroid", "flat", "smooth", "noperspective", "patch", "sample",
        "break", "continue", "do", "for", "while", "switch", "case", "default", "if", "else", "subroutine",
        "in", "out", "inout", "true", "false", "invariant", "precise", "discard", "return", "struct",
        "precision", "highp", "mediump", "lowp", "defined"
    )

    val TYPES: Set<String> = buildSet {
        addAll(listOf("void", "bool", "int", "uint", "float", "double", "atomic_uint"))
        for (n in 2..4) {
            for (prefix in listOf("", "i", "u", "b", "d")) add("${prefix}vec$n")
            for (prefix in listOf("", "d")) {
                add("${prefix}mat$n")
                for (m in 2..4) add("${prefix}mat${n}x$m")
            }
        }
        val dims = listOf("1D", "2D", "3D", "Cube", "2DRect", "1DArray", "2DArray", "CubeArray", "Buffer", "2DMS", "2DMSArray")
        for (prefix in listOf("", "i", "u")) {
            for (dim in dims) {
                add("${prefix}sampler$dim")
                add("${prefix}image$dim")
            }
        }
        for (dim in listOf("1D", "2D", "Cube", "2DRect", "1DArray", "2DArray", "CubeArray")) add("sampler${dim}Shadow")
    }

    val BUILTIN_FUNCTIONS = setOf(
        "radians", "degrees", "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "pow", "exp", "log", "exp2", "log2", "sqrt", "inversesqrt",
        "abs", "sign", "floor", "trunc", "round", "roundEven", "ceil", "fract", "mod", "modf", "min", "max", "clamp",
        "mix", "step", "smoothstep", "isnan", "isinf", "floatBitsToInt", "floatBitsToUint", "intBitsToFloat",
        "uintBitsToFloat", "fma", "frexp", "ldexp",
        "packUnorm2x16", "packSnorm2x16", "packUnorm4x8", "packSnorm4x8", "unpackUnorm2x16", "unpackSnorm2x16",
        "unpackUnorm4x8", "unpackSnorm4x8", "packHalf2x16", "unpackHalf2x16", "packDouble2x32", "unpackDouble2x32",
        "length", "distance", "dot", "cross", "normalize", "faceforward", "reflect", "refract",
        "matrixCompMult", "outerProduct", "transpose", "determinant", "inverse",
        "lessThan", "lessThanEqual", "greaterThan", "greaterThanEqual", "equal", "notEqual", "any", "all", "not",
        "uaddCarry", "usubBorrow", "umulExtended", "imulExtended", "bitfieldExtract", "bitfieldInsert",
        "bitfieldReverse", "bitCount", "findLSB", "findMSB",
        "textureSize", "textureQueryLod", "textureQueryLevels", "textureSamples", "texture", "textureProj",
        "textureLod", "textureOffset", "texelFetch", "texelFetchOffset", "textureProjOffset", "textureLodOffset",
        "textureProjLod", "textureProjLodOffset", "textureGrad", "textureGradOffset", "textureProjGrad",
        "textureProjGradOffset", "textureGather", "textureGatherOffset", "textureGatherOffsets",
        "texture2D", "texture2DLod", "texture3D", "textureCube", "shadow2D",
        "atomicCounterIncrement", "atomicCounterDecrement", "atomicCounter", "atomicAdd", "atomicMin", "atomicMax",
        "atomicAnd", "atomicOr", "atomicXor", "atomicExchange", "atomicCompSwap",
        "imageSize", "imageSamples", "imageLoad", "imageStore", "imageAtomicAdd", "imageAtomicMin", "imageAtomicMax",
        "imageAtomicAnd", "imageAtomicOr", "imageAtomicXor", "imageAtomicExchange", "imageAtomicCompSwap",
        "dFdx", "dFdy", "dFdxFine", "dFdyFine", "dFdxCoarse", "dFdyCoarse", "fwidth", "fwidthFine", "fwidthCoarse",
        "interpolateAtCentroid", "interpolateAtSample", "interpolateAtOffset",
        "EmitStreamVertex", "EndStreamPrimitive", "EmitVertex", "EndPrimitive",
        "barrier", "memoryBarrier", "memoryBarrierAtomicCounter", "memoryBarrierBuffer", "memoryBarrierShared",
        "memoryBarrierImage", "groupMemoryBarrier"
    )
}
