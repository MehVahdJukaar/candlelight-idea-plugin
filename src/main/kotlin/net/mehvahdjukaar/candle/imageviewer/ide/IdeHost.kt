package net.mehvahdjukaar.candle.imageviewer.ide

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CompositeShortcutSet
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ColorChooserService
import com.intellij.ui.awt.RelativePoint
import net.mehvahdjukaar.candle.imageviewer.platform.EditorHost
import net.mehvahdjukaar.candle.imageviewer.platform.EditorPopup
import net.mehvahdjukaar.candle.imageviewer.platform.EditorShortcuts
import net.mehvahdjukaar.candle.imageviewer.platform.ShortcutAction
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/** Backs the editor's host services with IntelliJ dialogs, popups, the color picker and keymap. */
object IdeHost : EditorHost {

    override fun error(parent: Component, message: String, title: String) {
        Messages.showErrorDialog(parent, message, title)
    }

    override fun confirmSave(parent: Component, message: String, title: String): Boolean {
        val choice = Messages.showYesNoDialog(
            message, title, "Save", "Don't Save", Messages.getQuestionIcon(),
        )
        return choice == Messages.YES
    }

    override fun chooseColor(parent: Component, title: String, initial: Color, enableOpacity: Boolean): Color? =
        ColorChooserService.getInstance().showDialog(parent, title, initial, enableOpacity)

    override fun showOkCancelDialog(parent: Component, title: String, content: JComponent, focus: JComponent?): Boolean {
        val dialogTitle = title
        val dialog = object : DialogWrapper(parent, true) {
            init {
                this.title = dialogTitle
                init()
            }

            override fun createCenterPanel(): JComponent = content
            override fun getPreferredFocusedComponent(): JComponent? = focus
        }
        return dialog.showAndGet()
    }

    override fun showPopup(
        content: JComponent,
        focus: JComponent?,
        title: String?,
        anchor: JComponent,
        x: Int,
        y: Int,
        below: Boolean,
        cancelOnClickOutside: Boolean,
        onClosed: Runnable,
    ): EditorPopup {
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, focus)
            .apply { if (title != null) setTitle(title) }
            .setResizable(false)
            .setMovable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(cancelOnClickOutside)
            .setCancelOnWindowDeactivation(cancelOnClickOutside)
            .createPopup()

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) = onClosed.run()
        })

        if (below) popup.showUnderneathOf(anchor) else popup.show(RelativePoint(anchor, Point(x, y)))
        return EditorPopup { popup.cancel() }
    }

    override fun bindShortcuts(component: JComponent, shortcuts: EditorShortcuts) {
        bindKeymap(component, IdeActions.ACTION_UNDO, shortcuts.undo)
        bindKeymap(component, IdeActions.ACTION_REDO, shortcuts.redo)
        bindKeymap(component, IdeActions.ACTION_COPY, shortcuts.copy)
        bindKeymap(component, IdeActions.ACTION_CUT, shortcuts.cut)
        bindKeymap(component, IdeActions.ACTION_PASTE, shortcuts.paste)

        // Ctrl+S explicitly: the IDE's "Save All" has no default keymap shortcut (autosave makes it
        // unnecessary), so relying on its shortcutSet would bind nothing. Merge in a user-assigned one.
        val ctrlS = CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK))
        val saveShortcuts = ActionManager.getInstance().getAction("SaveAll")?.shortcutSet
        val merged = if (saveShortcuts != null) CompositeShortcutSet(ctrlS, saveShortcuts) else ctrlS
        bindSet(component, merged, shortcuts.save)
    }

    private fun bindKeymap(component: JComponent, actionId: String, action: ShortcutAction) {
        val shortcutSet = ActionManager.getInstance().getAction(actionId)?.shortcutSet ?: return
        bindSet(component, shortcutSet, action)
    }

    private fun bindSet(component: JComponent, shortcutSet: ShortcutSet, action: ShortcutAction) {
        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) = action.run()
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = action.isEnabled()
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }.registerCustomShortcutSet(shortcutSet, component)
    }
}
