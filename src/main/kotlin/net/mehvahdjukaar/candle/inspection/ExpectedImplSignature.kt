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
            TypeConversionUtil.erasure(param.type).isAssignableFrom(TypeConversionUtil.erasure(expectedType))
        }
        if (!typeMatch) return false
        // Return type must match
        val implReturn = implMethod.returnType ?: PsiTypes.voidType()
        return TypeConversionUtil.erasure(implReturn).isAssignableFrom(TypeConversionUtil.erasure(returnType))
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
