package net.mehvahdjukaar.candle.imageviewer

import java.awt.Rectangle

/**
 * Frame-slicing state for previewing an image as an animation. The pixels live in the [ImageDocument];
 * this only records how to carve that image into equal frames and which frame is current.
 *
 * Both supported sources reduce to "one image sliced along its long axis":
 *  - a Minecraft-style sprite strip is already laid out that way, and
 *  - an animated GIF is composed into a vertical strip on load (see [GifStrip]),
 * so the rest of the editor never needs to know which it started as.
 */
class Animation {

    var imageW: Int = 1
        private set
    var imageH: Int = 1
        private set

    /** Number of equal frames the image is sliced into (>= 1). 1 means "not animated". */
    var frameCount: Int = 1
        private set

    /** Index of the frame currently shown, in `0 until frameCount`. */
    var currentFrame: Int = 0
        private set

    /** Playback speed: how long each frame is shown, in Minecraft ticks (20 ticks = 1 second). */
    var frameDurationTicks: Int = DEFAULT_DURATION_TICKS
        set(value) {
            field = value.coerceIn(1, MAX_DURATION_TICKS)
        }

    /** Strip orientation, inferred from the aspect ratio: tall images stack frames vertically. */
    val vertical: Boolean get() = imageH >= imageW

    val isAnimated: Boolean get() = frameCount > 1

    /** Upper bound for the frame count: at most one frame per pixel along the strip's long axis. */
    val maxFrames: Int get() = (if (vertical) imageH else imageW).coerceAtLeast(1)

    /** Re-seeds the model for a (possibly new) image, keeping the current frame in range. */
    fun reset(width: Int, height: Int, count: Int = frameCount) {
        imageW = width.coerceAtLeast(1)
        imageH = height.coerceAtLeast(1)
        setFrameCount(count)
    }

    fun setFrameCount(count: Int) {
        frameCount = count.coerceIn(1, maxFrames)
        currentFrame = currentFrame.coerceIn(0, frameCount - 1)
    }

    /** Picks a frame count assuming the frames are square (the Minecraft strip convention). */
    fun autoDetectSquareFrames() {
        val short = minOf(imageW, imageH).coerceAtLeast(1)
        val long = maxOf(imageW, imageH)
        setFrameCount(long / short)
    }

    /** Selects a frame, wrapping around so stepping past either end loops. */
    fun goTo(index: Int) {
        if (frameCount <= 0) return
        currentFrame = ((index % frameCount) + frameCount) % frameCount
    }

    fun next() = goTo(currentFrame + 1)

    /** The image-space rectangle covered by frame [index]. */
    fun frameRect(index: Int = currentFrame): Rectangle =
        if (vertical) {
            val fh = imageH / frameCount
            Rectangle(0, index * fh, imageW, fh)
        } else {
            val fw = imageW / frameCount
            Rectangle(index * fw, 0, fw, imageH)
        }

    companion object {
        const val DEFAULT_DURATION_TICKS = 1
        const val MAX_DURATION_TICKS = 1200
    }
}
