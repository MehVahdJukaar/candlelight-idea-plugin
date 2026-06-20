package net.mehvahdjukaar.candle.usage

import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageGroupingRulesDefaultRanks
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleProvider
import com.intellij.usages.rules.UsageInModule
import net.mehvahdjukaar.candle.settings.CandleSettings
import net.mehvahdjukaar.candle.util.CandleBundle
import net.mehvahdjukaar.candle.util.ModuleRole
import net.mehvahdjukaar.candle.util.ModuleRoleDetector

class PlatformUsageGroupingRule : SingleParentUsageGroupingRule() {

    override fun getParentGroupFor(usage: Usage, usageTargets: Array<out UsageTarget>): UsageGroup? {
        return PlatformUsageGroup(detectRole(usage))
    }

    override fun getRank(): Int = UsageGroupingRulesDefaultRanks.AFTER_FILE_STRUCTURE.absoluteRank

    private fun detectRole(usage: Usage): ModuleRole {
        if (usage is PsiElementUsage) {
            usage.element?.let { return ModuleRoleDetector.detectRole(it) }
        }
        if (usage is UsageInModule) {
            usage.module?.let { module ->
                ModuleRoleDetector.detectRole(module)
                    .takeIf { it != ModuleRole.UNKNOWN }
                    ?.let { return it }
            }
        }
        return ModuleRole.UNKNOWN
    }

    private class PlatformUsageGroup(private val role: ModuleRole) : UsageGroup {
        override fun getPresentableGroupText(): String = role.toUsageGroupLabel()

        override fun getTextAttributes(isSelected: Boolean): SimpleTextAttributes =
            if (isSelected) SimpleTextAttributes.GRAY_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES

        override fun compareTo(other: UsageGroup): Int {
            if (other !is PlatformUsageGroup) return 0
            return role.toSortOrder().compareTo(other.role.toSortOrder())
        }
    }
}

private fun ModuleRole.toUsageGroupLabel(): String = when (this) {
    ModuleRole.COMMON -> CandleBundle["settings.usageGroup.common"]
    ModuleRole.FABRIC -> CandleBundle["settings.usageGroup.fabric"]
    ModuleRole.NEOFORGE -> CandleBundle["settings.usageGroup.neoforge"]
    ModuleRole.FORGE -> CandleBundle["settings.usageGroup.forge"]
    ModuleRole.UNKNOWN -> CandleBundle["settings.usageGroup.unknown"]
}

private fun ModuleRole.toSortOrder(): Int = when (this) {
    ModuleRole.COMMON -> 0
    ModuleRole.FABRIC -> 1
    ModuleRole.NEOFORGE -> 2
    ModuleRole.FORGE -> 3
    ModuleRole.UNKNOWN -> 4
}

class PlatformUsageGroupingRuleProvider : UsageGroupingRuleProvider {
    override fun getActiveRules(project: Project): Array<UsageGroupingRule> {
        if (!CandleSettings.getInstance(project).groupFindUsagesByPlatform) {
            return UsageGroupingRule.EMPTY_ARRAY
        }
        return arrayOf(PlatformUsageGroupingRule())
    }
}
