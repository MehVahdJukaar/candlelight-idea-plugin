package net.mehvahdjukaar.candle.imageviewer.ide

import net.mehvahdjukaar.candle.imageviewer.platform.Host
import net.mehvahdjukaar.candle.imageviewer.platform.Icons
import net.mehvahdjukaar.candle.imageviewer.platform.Ui

object IdePlatform {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            Ui.install(IdeUiBackend)
            Icons.install(IdeIconSet)
            Host.install(IdeHost)
            installed = true
        }
    }
}
