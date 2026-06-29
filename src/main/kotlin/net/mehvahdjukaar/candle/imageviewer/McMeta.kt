package net.mehvahdjukaar.candle.imageviewer

import com.intellij.openapi.vfs.VirtualFile

/** Frame count and per-frame duration recovered from a Minecraft `.mcmeta` animation block. */
class McMetaAnimation(val frameCount: Int, val frameDurationTicks: Int)

/**
 * Reads the sibling `<texture>.mcmeta` that Minecraft uses to describe an animated texture, so the
 * editor can pre-fill the Animation controls when one is opened.
 *
 * The format is small and flat, e.g. `{ "animation": { "frametime": 2, "height": 16 } }`, so the few
 * scalar fields we need are pulled out directly rather than pulling in a JSON dependency. Minecraft
 * always stacks frames vertically; the frame height is the mcmeta `height`, or a square frame (the
 * texture's narrow side) when absent, and the frame count follows from the image height.
 */
object McMeta {

    fun readFor(file: VirtualFile, imageW: Int, imageH: Int): McMetaAnimation? {
        val sidecar = file.parent?.findChild("${file.name}.mcmeta") ?: return null
        val text = runCatching { String(sidecar.contentsToByteArray(), Charsets.UTF_8) }.getOrNull() ?: return null
        if (!text.contains("animation")) return null

        val frameHeight = intField(text, "height") ?: minOf(imageW, imageH)
        if (frameHeight <= 0) return null
        val count = imageH / frameHeight
        if (count <= 1) return null // a single frame isn't an animation

        val frametime = (intField(text, "frametime") ?: 1).coerceAtLeast(1)
        return McMetaAnimation(count, frametime)
    }

    private fun intField(text: String, name: String): Int? =
        Regex("\"$name\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
}
