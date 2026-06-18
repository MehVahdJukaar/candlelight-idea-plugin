package net.mehvahdjukaar.candle.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "CandleSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class CandleSettings : PersistentStateComponent<CandleSettings.State> {

    data class State(
        var psiCachingEnabled: Boolean = true,
        var tabTitlePrefixesEnabled: Boolean = true,
        var groupFindUsagesByPlatform: Boolean = true,
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

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun getInstance(project: Project): CandleSettings = project.getService(CandleSettings::class.java)
    }
}
