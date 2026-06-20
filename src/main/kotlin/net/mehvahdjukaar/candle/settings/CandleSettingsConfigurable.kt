package net.mehvahdjukaar.candle.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabelDecorator
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import net.mehvahdjukaar.candle.util.CandleBundle
import javax.swing.JComponent

class CandleSettingsConfigurable(private val project: Project) : Configurable, SearchableConfigurable {

    private val settings = CandleSettings.getInstance(project)

    private var psiCachingCheckBox: JBCheckBox? = null
    private var tabPrefixesCheckBox: JBCheckBox? = null
    private var findUsagesGroupingCheckBox: JBCheckBox? = null
    private var navigationPrefixesCheckBox: JBCheckBox? = null
    private var projectViewPrefixesCheckBox: JBCheckBox? = null
    private var commonPrefixCheckBox: JBCheckBox? = null
    private var fabricPrefixCheckBox: JBCheckBox? = null
    private var neoForgePrefixCheckBox: JBCheckBox? = null
    private var forgePrefixCheckBox: JBCheckBox? = null

    private var panel: DialogPanel? = null

    override fun getId(): String = "net.mehvahdjukaar.candle.settings"

    override fun getDisplayName(): String = CandleBundle["settings.display.name"]

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent {
        panel = panel {
            row {
                cell(
                    JBLabelDecorator.createJBLabelDecorator().setBold(true).apply {
                        text = CandleBundle["settings.section.performance"]
                    }
                ).align(AlignX.LEFT)
            }
            row {
                psiCachingCheckBox = checkBox(CandleBundle["settings.psiCachingEnabled"])
                    .comment(CandleBundle["settings.psiCachingEnabled.hint"])
                    .component
            }

            row {
                cell(
                    JBLabelDecorator.createJBLabelDecorator().setBold(true).apply {
                        text = CandleBundle["settings.section.visual"]
                    }
                ).align(AlignX.LEFT)
            }.topGap(TopGap.SMALL)

            row {
                tabPrefixesCheckBox = checkBox(CandleBundle["settings.tabTitlePrefixesEnabled"])
                    .comment(CandleBundle["settings.tabTitlePrefixesEnabled.hint"])
                    .component
            }
            row {
                navigationPrefixesCheckBox = checkBox(CandleBundle["settings.navigationPlatformPrefixesEnabled"])
                    .comment(CandleBundle["settings.navigationPlatformPrefixesEnabled.hint"])
                    .component
            }
            row {
                projectViewPrefixesCheckBox = checkBox(CandleBundle["settings.projectViewPlatformPrefixesEnabled"])
                    .comment(CandleBundle["settings.projectViewPlatformPrefixesEnabled.hint"])
                    .component
            }
            row {
                findUsagesGroupingCheckBox = checkBox(CandleBundle["settings.groupFindUsagesByPlatform"])
                    .comment(CandleBundle["settings.groupFindUsagesByPlatform.hint"])
                    .component
            }

            row {
                cell(
                    JBLabelDecorator.createJBLabelDecorator().setBold(false).apply {
                        text = CandleBundle["settings.section.platformPrefixes"]
                    }
                ).align(AlignX.LEFT)
            }.topGap(TopGap.SMALL)

            row {
                commonPrefixCheckBox = checkBox(CandleBundle["settings.showCommonPrefix"]).component
                fabricPrefixCheckBox = checkBox(CandleBundle["settings.showFabricPrefix"]).component
            }
            row {
                neoForgePrefixCheckBox = checkBox(CandleBundle["settings.showNeoForgePrefix"]).component
                forgePrefixCheckBox = checkBox(CandleBundle["settings.showForgePrefix"]).component
            }
            row {
                comment(CandleBundle["settings.platformPrefixes.hint"])
            }
        }
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val psiCaching = psiCachingCheckBox ?: return false
        val tabPrefixes = tabPrefixesCheckBox ?: return false
        val navigationPrefixes = navigationPrefixesCheckBox ?: return false
        val projectViewPrefixes = projectViewPrefixesCheckBox ?: return false
        val findUsages = findUsagesGroupingCheckBox ?: return false
        val commonPrefix = commonPrefixCheckBox ?: return false
        val fabricPrefix = fabricPrefixCheckBox ?: return false
        val neoForgePrefix = neoForgePrefixCheckBox ?: return false
        val forgePrefix = forgePrefixCheckBox ?: return false
        return psiCaching.isSelected != settings.psiCachingEnabled ||
            tabPrefixes.isSelected != settings.tabTitlePrefixesEnabled ||
            navigationPrefixes.isSelected != settings.navigationPlatformPrefixesEnabled ||
            projectViewPrefixes.isSelected != settings.projectViewPlatformPrefixesEnabled ||
            findUsages.isSelected != settings.groupFindUsagesByPlatform ||
            commonPrefix.isSelected != settings.showCommonPrefix ||
            fabricPrefix.isSelected != settings.showFabricPrefix ||
            neoForgePrefix.isSelected != settings.showNeoForgePrefix ||
            forgePrefix.isSelected != settings.showForgePrefix
    }

    override fun apply() {
        settings.psiCachingEnabled = psiCachingCheckBox?.isSelected ?: true
        settings.tabTitlePrefixesEnabled = tabPrefixesCheckBox?.isSelected ?: true
        settings.navigationPlatformPrefixesEnabled = navigationPrefixesCheckBox?.isSelected ?: true
        settings.projectViewPlatformPrefixesEnabled = projectViewPrefixesCheckBox?.isSelected ?: false
        settings.groupFindUsagesByPlatform = findUsagesGroupingCheckBox?.isSelected ?: true
        settings.showCommonPrefix = commonPrefixCheckBox?.isSelected ?: false
        settings.showFabricPrefix = fabricPrefixCheckBox?.isSelected ?: true
        settings.showNeoForgePrefix = neoForgePrefixCheckBox?.isSelected ?: true
        settings.showForgePrefix = forgePrefixCheckBox?.isSelected ?: true
    }

    override fun reset() {
        psiCachingCheckBox?.isSelected = settings.psiCachingEnabled
        tabPrefixesCheckBox?.isSelected = settings.tabTitlePrefixesEnabled
        navigationPrefixesCheckBox?.isSelected = settings.navigationPlatformPrefixesEnabled
        projectViewPrefixesCheckBox?.isSelected = settings.projectViewPlatformPrefixesEnabled
        findUsagesGroupingCheckBox?.isSelected = settings.groupFindUsagesByPlatform
        commonPrefixCheckBox?.isSelected = settings.showCommonPrefix
        fabricPrefixCheckBox?.isSelected = settings.showFabricPrefix
        neoForgePrefixCheckBox?.isSelected = settings.showNeoForgePrefix
        forgePrefixCheckBox?.isSelected = settings.showForgePrefix
    }

    override fun disposeUIResources() {
        panel = null
        psiCachingCheckBox = null
        tabPrefixesCheckBox = null
        navigationPrefixesCheckBox = null
        projectViewPrefixesCheckBox = null
        findUsagesGroupingCheckBox = null
        commonPrefixCheckBox = null
        fabricPrefixCheckBox = null
        neoForgePrefixCheckBox = null
        forgePrefixCheckBox = null
    }
}
