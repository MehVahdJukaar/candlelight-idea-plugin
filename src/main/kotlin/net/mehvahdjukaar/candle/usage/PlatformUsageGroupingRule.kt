package net.mehvahdjukaar.candle.usage

import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageInfo2
import com.intellij.usages.impl.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleEx
import net.mehvahdjukaar.candle.settings.CandleSettings
import net.mehvahdjukaar.candle.util.CandleBundle
import net.mehvahdjukaar.candle.util.ModuleRole
import net.mehvahdjukaar.candle.util.ModuleRoleDetector

class PlatformUsageGroupingRule(private val project: Project) : SingleParentUsageGroupingRule() {

    override fun getParentGroupFor(usage: Usage, usages: Array<out Usage>): UsageGroup {
        val role = detectRole(usage)
        return PlatformUsageGroup(role)
    }

    private fun detectRole(usage: Usage): ModuleRole {
        val element = usage.navigationElement
        if (element != null) {
            return ModuleRoleDetector.detectRole(element)
        }
        if (usage is UsageInfo2) {
            val psi = usage.element
            if (psi != null) {
                return ModuleRoleDetector.detectRole(psi)
            }
        }
        return ModuleRole.UNKNOWN
    }

    override fun getRank(): UsageGroupingRule.Rank = UsageGroupingRule.Rank.POST_FILE_STRUCTURE

    private class PlatformUsageGroup(private val role: ModuleRole) : UsageGroup {
        override fun getPresentableGroupText(): String = role.usageGroupLabel

        override fun getIcon(): javax.swing.Icon? = null

        override fun isValid(): Boolean = true

        override fun update(): Unit = Unit

        override fun navigate(focus: Boolean): Boolean = false

        override fun canNavigate(): Boolean = false

        override fun canNavigateToFocus(): Boolean = false

        override fun compareTo(other: UsageGroup?): Int {
            if (other !is PlatformUsageGroup) return 0
            return role.sortOrder.compareTo(other.role.sortOrder)
        }
    }

    private val ModuleRole.usageGroupLabel: String
        get() = when (this) {
            ModuleRole.COMMON -> CandleBundle["settings.usageGroup.common"]
            ModuleRole.FABRIC -> CandleBundle["settings.usageGroup.fabric"]
            ModuleRole.NEOFORGE -> CandleBundle["settings.usageGroup.neoforge"]
            ModuleRole.FORGE -> CandleBundle["settings.usageGroup.forge"]
            ModuleRole.UNKNOWN -> CandleBundle["settings.usageGroup.unknown"]
        }

    private val ModuleRole.sortOrder: Int
        get() = when (this) {
            ModuleRole.COMMON -> 0
            ModuleRole.FABRIC -> 1
            ModuleRole.NEOFORGE -> 2
            ModuleRole.FORGE -> 3
            ModuleRole.UNKNOWN -> 4
        }
}

class PlatformUsageGroupingRuleProvider : UsageGroupingRuleEx {
    override fun getActiveRules(project: Project): Array<UsageGroupingRule> {
        if (!CandleSettings.getInstance(project).groupFindUsagesByPlatform) {
            return UsageGroupingRule.EMPTY_ARRAY
        }
        return arrayOf(PlatformUsageGroupingRule(project))
    }
}
