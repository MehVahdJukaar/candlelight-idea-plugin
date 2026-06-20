package net.mehvahdjukaar.candle.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import net.mehvahdjukaar.candle.util.ModuleRole

@Service(Service.Level.PROJECT)
@State(name = "CandleSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class CandleSettings : PersistentStateComponent<CandleSettings.State> {

    data class State(
        var psiCachingEnabled: Boolean = true,
        var tabTitlePrefixesEnabled: Boolean = true,
        var groupFindUsagesByPlatform: Boolean = true,
        var navigationPlatformPrefixesEnabled: Boolean = true,
        var projectViewPlatformPrefixesEnabled: Boolean = false,
        var showCommonPrefix: Boolean = false,
        var showFabricPrefix: Boolean = true,
        var showNeoForgePrefix: Boolean = true,
        var showForgePrefix: Boolean = true,
    )

    private var state = State()

    var psiCachingEnabled: Boolean
        get() = state.psiCachingEnabled
        set(value) {
            state.psiCachingEnabled = value
        }

    var tabTitlePrefixesEnabled: Boolean
        get() = state.tabTitlePrefixesEnabled
        set(value) {
            state.tabTitlePrefixesEnabled = value
        }

    var groupFindUsagesByPlatform: Boolean
        get() = state.groupFindUsagesByPlatform
        set(value) {
            state.groupFindUsagesByPlatform = value
        }

    var navigationPlatformPrefixesEnabled: Boolean
        get() = state.navigationPlatformPrefixesEnabled
        set(value) {
            state.navigationPlatformPrefixesEnabled = value
        }

    var projectViewPlatformPrefixesEnabled: Boolean
        get() = state.projectViewPlatformPrefixesEnabled
        set(value) {
            state.projectViewPlatformPrefixesEnabled = value
        }

    var showCommonPrefix: Boolean
        get() = state.showCommonPrefix
        set(value) {
            state.showCommonPrefix = value
        }

    var showFabricPrefix: Boolean
        get() = state.showFabricPrefix
        set(value) {
            state.showFabricPrefix = value
        }

    var showNeoForgePrefix: Boolean
        get() = state.showNeoForgePrefix
        set(value) {
            state.showNeoForgePrefix = value
        }

    var showForgePrefix: Boolean
        get() = state.showForgePrefix
        set(value) {
            state.showForgePrefix = value
        }

    fun isPrefixEnabled(role: ModuleRole): Boolean = when (role) {
        ModuleRole.COMMON -> showCommonPrefix
        ModuleRole.FABRIC -> showFabricPrefix
        ModuleRole.NEOFORGE -> showNeoForgePrefix
        ModuleRole.FORGE -> showForgePrefix
        ModuleRole.UNKNOWN -> false
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun getInstance(project: Project): CandleSettings = project.getService(CandleSettings::class.java)
    }
}
