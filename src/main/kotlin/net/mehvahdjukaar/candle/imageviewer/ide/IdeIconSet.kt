package net.mehvahdjukaar.candle.imageviewer.ide

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import net.mehvahdjukaar.candle.imageviewer.platform.IconSet
import javax.swing.Icon

/** Maps the editor's named icons onto IntelliJ's `AllIcons`, and loads the bundled tool SVGs. */
object IdeIconSet : IconSet {
    override fun undo(): Icon = AllIcons.Actions.Undo
    override fun redo(): Icon = AllIcons.Actions.Redo
    override fun save(): Icon = AllIcons.Actions.MenuSaveall
    override fun copy(): Icon = AllIcons.Actions.Copy
    override fun cut(): Icon = AllIcons.Actions.MenuCut
    override fun paste(): Icon = AllIcons.Actions.MenuPaste

    override fun play(): Icon = AllIcons.Actions.Execute
    override fun pause(): Icon = AllIcons.Actions.Pause
    override fun prevFrame(): Icon = AllIcons.Actions.Play_back
    override fun nextFrame(): Icon = AllIcons.Actions.Play_forward
    override fun autoDetect(): Icon = AllIcons.Actions.Lightning

    override fun zoomIn(): Icon = AllIcons.General.ZoomIn
    override fun zoomOut(): Icon = AllIcons.General.ZoomOut
    override fun fit(): Icon = AllIcons.General.FitContent

    override fun arrowUp(): Icon = AllIcons.General.ArrowUp
    override fun arrowDown(): Icon = AllIcons.General.ArrowDown
    override fun arrowRight(): Icon = AllIcons.General.ArrowRight

    override fun add(): Icon = AllIcons.General.Add
    override fun remove(): Icon = AllIcons.General.Remove
    override fun merge(): Icon = AllIcons.General.CollapseComponent

    override fun resource(path: String): Icon = IconLoader.getIcon(path, IdeIconSet::class.java)
}
