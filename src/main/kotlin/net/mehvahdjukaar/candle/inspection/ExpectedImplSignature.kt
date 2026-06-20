package net.mehvahdjukaar.candle.inspection

import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil

data class ExpectedImplSignature(
    val name: String,
    val returnType: PsiType,
    val parameterTypes: List<PsiType>
) {


    fun matchesImplMethod(implMethod: PsiMethod): Boolean {
        // Implementation method must be static
        if (!implMethod.hasModifierProperty(PsiModifier.STATIC)) return false
        // Name must match
        if (implMethod.name != name) return false
        // Parameter types must match exactly
        val implParams = implMethod.parameterList.parameters
        if (implParams.size != parameterTypes.size) return false
        val typeMatch = implParams.zip(parameterTypes).all { (param, expectedType) ->
            erasedTypesEqual(expectedType, param.type)
        }
        if (!typeMatch) return false
        val implReturn = implMethod.returnType ?: PsiTypes.voidType()
        return erasedTypesEqual(returnType, implReturn)
    }

    private fun erasedTypesEqual(expected: PsiType, actual: PsiType): Boolean {
        val expectedErased = TypeConversionUtil.erasure(expected) ?: return false
        val actualErased = TypeConversionUtil.erasure(actual) ?: return false
        return expectedErased.canonicalText == actualErased.canonicalText
    }

    companion object {
        fun fromExpectMethod(method: PsiMethod): ExpectedImplSignature {
            val project = method.project
            val elementFactory = JavaPsiFacade.getElementFactory(project)
            val paramTypes = mutableListOf<PsiType>()

            // For instance methods, add the containing class as first parameter
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                val containingClass = method.containingClass!!
                val classType = PsiType.getTypeByName(
                    containingClass.qualifiedName!!,
                    project,
                    method.resolveScope
                ) ?: elementFactory.createType(containingClass)
                paramTypes.add(classType)
            }

            // Add original method parameters
            method.parameterList.parameters.mapTo(paramTypes) { it.type }

            return ExpectedImplSignature(
                name = method.name,
                returnType = method.returnType ?: PsiTypes.voidType(),
                parameterTypes = paramTypes
            )
        }
    }
}
