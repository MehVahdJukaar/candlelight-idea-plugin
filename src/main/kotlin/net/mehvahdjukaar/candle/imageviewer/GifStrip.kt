package net.mehvahdjukaar.candle.imageviewer

import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.roundToInt

/** An animated GIF decoded into a single vertical sprite strip, plus its frame count and timing. */
class DecodedGif(val strip: BufferedImage, val frameCount: Int, val frameDurationTicks: Int)

/**
 * Decodes an animated GIF into a vertical sprite strip: every fully-composed frame stacked top to
 * bottom in one [BufferedImage], so the editor can treat it identically to a Minecraft animation
 * strip. GIF frames may be partial (a sub-rectangle with a disposal rule), so each frame is painted
 * onto a running canvas before it is captured.
 */
object GifStrip {

    /** Returns null if the bytes aren't a GIF or hold only a single frame (nothing to animate). */
    fun decode(bytes: ByteArray): DecodedGif? {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
        val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: run {
            stream.close(); return null
        }
        try {
            reader.input = stream
            val count = reader.getNumImages(true)
            if (count <= 1) return null

            val (canvasW, canvasH) = logicalScreenSize(reader)
            var canvas = BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB)
            val frames = ArrayList<BufferedImage>(count)
            val delaysCs = ArrayList<Int>(count)

            for (i in 0 until count) {
                val frame = reader.read(i)
                val meta = frameMeta(reader, i)
                canvas.createGraphics().apply {
                    drawImage(frame, meta.x, meta.y, null)
                    dispose()
                }
                frames.add(copyOf(canvas))
                delaysCs.add(meta.delayCs)
                // Only the "clear back to transparent" disposal needs handling; the others
                // ("do not dispose"/unspecified) leave the canvas to accumulate.
                if (meta.disposal == DISPOSE_TO_BACKGROUND) {
                    canvas = clearRegion(canvas, meta.x, meta.y, frame.width, frame.height)
                }
            }

            return DecodedGif(stack(frames, canvasW, canvasH), frames.size, ticksFor(delaysCs))
        } catch (t: Throwable) {
            return null
        } finally {
            reader.dispose()
            stream.close()
        }
    }

    private class FrameMeta(val x: Int, val y: Int, val delayCs: Int, val disposal: String)

    private fun frameMeta(reader: javax.imageio.ImageReader, index: Int): FrameMeta {
        val root = runCatching {
            reader.getImageMetadata(index).getAsTree(GIF_IMAGE_FORMAT) as IIOMetadataNode
        }.getOrNull() ?: return FrameMeta(0, 0, 0, "none")
        val gce = child(root, "GraphicControlExtension")
        val desc = child(root, "ImageDescriptor")
        return FrameMeta(
            x = attrInt(desc, "imageLeftPosition"),
            y = attrInt(desc, "imageTopPosition"),
            delayCs = attrInt(gce, "delayTime"),
            disposal = gce?.getAttribute("disposalMethod") ?: "none",
        )
    }

    private fun logicalScreenSize(reader: javax.imageio.ImageReader): Pair<Int, Int> {
        val fallback = reader.getWidth(0) to reader.getHeight(0)
        val root = runCatching {
            reader.streamMetadata?.getAsTree(GIF_STREAM_FORMAT) as? IIOMetadataNode
        }.getOrNull() ?: return fallback
        val lsd = child(root, "LogicalScreenDescriptor") ?: return fallback
        val w = attrInt(lsd, "logicalScreenWidth").takeIf { it > 0 } ?: fallback.first
        val h = attrInt(lsd, "logicalScreenHeight").takeIf { it > 0 } ?: fallback.second
        return w to h
    }

    /** Stacks equally-sized [frames] into one tall strip ([w] x [h]*count). */
    private fun stack(frames: List<BufferedImage>, w: Int, h: Int): BufferedImage {
        val strip = BufferedImage(w, h * frames.size, BufferedImage.TYPE_INT_ARGB)
        strip.createGraphics().apply {
            frames.forEachIndexed { i, frame -> drawImage(frame, 0, i * h, null) }
            dispose()
        }
        return strip
    }

    private fun copyOf(img: BufferedImage): BufferedImage {
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
        return out
    }

    private fun clearRegion(img: BufferedImage, x: Int, y: Int, w: Int, h: Int): BufferedImage {
        val out = copyOf(img)
        out.createGraphics().apply {
            composite = AlphaComposite.Clear
            fillRect(x, y, w, h)
            dispose()
        }
        return out
    }

    /** GIF delays are in centiseconds; convert the most common one to ticks (50 ms each), min 1. */
    private fun ticksFor(delaysCs: List<Int>): Int {
        val typical = delaysCs.filter { it > 0 }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: DEFAULT_DELAY_CS
        return (typical * MS_PER_CS / MS_PER_TICK).roundToInt().coerceAtLeast(1)
    }

    private fun child(parent: IIOMetadataNode?, name: String): IIOMetadataNode? {
        val list = parent?.getElementsByTagName(name) ?: return null
        return if (list.length > 0) list.item(0) as IIOMetadataNode else null
    }

    private fun attrInt(node: IIOMetadataNode?, name: String): Int =
        node?.getAttribute(name)?.toIntOrNull() ?: 0

    private const val GIF_IMAGE_FORMAT = "javax_imageio_gif_image_1.0"
    private const val GIF_STREAM_FORMAT = "javax_imageio_gif_stream_1.0"
    private const val DISPOSE_TO_BACKGROUND = "restoreToBackgroundColor"
    private const val DEFAULT_DELAY_CS = 10
    private const val MS_PER_CS = 10.0
    private const val MS_PER_TICK = 50.0
}
