package net.mehvahdjukaar.candle.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.PsiModificationTracker
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabelDecorator
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import net.mehvahdjukaar.candle.util.CandleBundle
import javax.swing.JComponent

class CandleSettingsConfigurable(private val project: Project) : Configurable, SearchableConfigurable {

    private val settings = CandleSettings.getInstance(project)

    private var psiCachingCheckBox: JBCheckBox? = null
    private var tabPrefixesCheckBox: JBCheckBox? = null
    private var findUsagesGroupingCheckBox: JBCheckBox? = null

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
            }.topGap(JBUI.scale(8))

            row {
                tabPrefixesCheckBox = checkBox(CandleBundle["settings.tabTitlePrefixesEnabled"])
                    .comment(CandleBundle["settings.tabTitlePrefixesEnabled.hint"])
                    .component
            }
            row {
                findUsagesGroupingCheckBox = checkBox(CandleBundle["settings.groupFindUsagesByPlatform"])
                    .comment(CandleBundle["settings.groupFindUsagesByPlatform.hint"])
                    .component
            }
        }
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val psiCaching = psiCachingCheckBox ?: return false
        val tabPrefixes = tabPrefixesCheckBox ?: return false
        val findUsages = findUsagesGroupingCheckBox ?: return false
        return psiCaching.isSelected != settings.psiCachingEnabled ||
            tabPrefixes.isSelected != settings.tabTitlePrefixesEnabled ||
            findUsages.isSelected != settings.groupFindUsagesByPlatform
    }

    override fun apply() {
        val previousCaching = settings.psiCachingEnabled
        settings.psiCachingEnabled = psiCachingCheckBox?.isSelected ?: true
        settings.tabTitlePrefixesEnabled = tabPrefixesCheckBox?.isSelected ?: true
        settings.groupFindUsagesByPlatform = findUsagesGroupingCheckBox?.isSelected ?: true

        if (previousCaching != settings.psiCachingEnabled) {
            PsiModificationTracker.getInstance(project).incModificationCount()
        }
    }

    override fun reset() {
        psiCachingCheckBox?.isSelected = settings.psiCachingEnabled
        tabPrefixesCheckBox?.isSelected = settings.tabTitlePrefixesEnabled
        findUsagesGroupingCheckBox?.isSelected = settings.groupFindUsagesByPlatform
    }

    override fun disposeUIResources() {
        panel = null
        psiCachingCheckBox = null
        tabPrefixesCheckBox = null
        findUsagesGroupingCheckBox = null
    }
}
