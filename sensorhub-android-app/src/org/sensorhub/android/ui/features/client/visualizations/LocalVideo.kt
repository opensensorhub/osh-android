package org.sensorhub.android.ui.features.client.visualizations

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import android.view.TextureView
import java.nio.ByteBuffer
import java.util.ArrayDeque

enum class PtzCommand(val item: String, val delta: Double) {
    PAN_LEFT("rpan", -5.0),
    PAN_RIGHT("rpan", 5.0),
    TILT_UP("rtilt", 5.0),
    TILT_DOWN("rtilt", -5.0),
    ZOOM_IN("rzoom", 1000.0),
    ZOOM_OUT("rzoom", -1000.0),
//    HOME("home", 0)
}

class VideoStream(
    private val streamIds: Collection<String>,
    private val name: String?,
    private val width: Int,
    private val height: Int,
) {
    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var sawKeyframe = false
    private var ptsIndex = 0L
    private val pending = ArrayDeque<ByteArray>()
    private val freeInputs = ArrayDeque<Int>()
    private val lock = Any()
    private val jpegPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun attach(view: TextureView) {
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                st: SurfaceTexture,
                w: Int,
                h: Int
            ) {
                surface?.release(); surface = Surface(st)
            }

            override fun onSurfaceTextureSizeChanged(
                st: SurfaceTexture,
                w: Int,
                h: Int
            ) = Unit

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                disconnect(); return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
        if (view.isAvailable) {
            surface?.release()
            surface = Surface(view.surfaceTexture)
        }
    }

    fun update(timestamp: Long, record: ByteArray) {
        if (record.size <= HEADER_SIZE || surface == null) return
        jpegOffset(record)?.let { renderJpeg(record, it); return }
        val unit = toAnnexB(record) ?: return
        if (!sawKeyframe) {
            if (!isKeyframe(unit)) return
            sawKeyframe = true
        }
        val decoder = codec ?: startDecoder(isAnnexB(record, HEADER_SIZE)) ?: return
        synchronized(lock) {
            freeInputs.poll()?.let { submit(decoder, it, unit) } ?: run {
                if (pending.size == MAX_PENDING) pending.poll()
                pending.add(unit)
            }
        }
    }

    fun disconnect() {
        synchronized(lock) { pending.clear(); freeInputs.clear() }
        releaseDecoder()
        surface?.release(); surface = null
        sawKeyframe = false; ptsIndex = 0L
    }

    private fun startDecoder(inBandParameterSets: Boolean): MediaCodec? {
        val target = surface ?: return null
        return runCatching {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also { decoder ->
                decoder.setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        synchronized(lock) {
                            pending.poll()?.let { submit(codec, index, it) }
                                ?: freeInputs.add(index)
                        }
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        runCatching { codec.releaseOutputBuffer(index, true) }
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) =
                        Unit

                    override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) {
                        releaseDecoder()
                    }
                })
                val format =
                    MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
                        .apply {
                            setInteger(
                                MediaFormat.KEY_MAX_WIDTH,
                                1920
                            ); setInteger(MediaFormat.KEY_MAX_HEIGHT, 1080)
                            if (!inBandParameterSets) {
                                setByteBuffer(
                                    "csd-0",
                                    ByteBuffer.wrap(BBB_SPS)
                                ); setByteBuffer("csd-1", ByteBuffer.wrap(BBB_PPS))
                            }
                        }
                decoder.configure(format, target, null, 0); decoder.start(); codec = decoder
            }
        }.getOrNull()
    }

    private fun releaseDecoder() {
        val old = codec ?: return
        codec = null
        runCatching { old.stop() }; runCatching { old.release() }
    }

    private fun submit(decoder: MediaCodec, index: Int, unit: ByteArray) = runCatching {
        decoder.getInputBuffer(index)?.apply {
            clear()
            if (remaining() >= unit.size) {
                put(unit); decoder.queueInputBuffer(index, 0, unit.size, ptsIndex++ * 33_333L, 0)
            } else decoder.queueInputBuffer(index, 0, 0, 0, 0)
        }
    }

    private fun renderJpeg(data: ByteArray, offset: Int) {
        val bitmap = BitmapFactory.decodeByteArray(data, offset, data.size - offset) ?: return
        val target = surface ?: return
        runCatching {
            target.lockCanvas(null).also { canvas ->
                try {
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        Rect(0, 0, canvas.width, canvas.height),
                        jpegPaint
                    )
                } finally {
                    target.unlockCanvasAndPost(canvas)
                }
            }
        }
        bitmap.recycle()
    }

    private fun toAnnexB(data: ByteArray): ByteArray? {
        if (isAnnexB(data, HEADER_SIZE)) return data.copyOfRange(HEADER_SIZE, data.size)
        val input = ByteBuffer.wrap(data, HEADER_SIZE, data.size - HEADER_SIZE)
        val output = ByteArray(input.remaining())
        var count = 0
        while (input.remaining() >= 4) {
            val length = input.int
            if (length <= 0 || length > input.remaining()) break
            START_CODE.copyInto(output, count); count += START_CODE.size
            input.get(output, count, length); count += length
        }
        return output.copyOf(count).takeIf { it.isNotEmpty() }
    }

    private fun isKeyframe(data: ByteArray): Boolean {
        for (i in 0 until data.size - 4) if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && (data[i + 2] == 1.toByte() || (data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()))) {
            val header = if (data[i + 2] == 1.toByte()) i + 3 else i + 4
            if (header < data.size && (data[header].toInt() and 0x1F) in 5..7) return true
        }
        return false
    }

    private fun isAnnexB(data: ByteArray, offset: Int) =
        data.size - offset >= 4 && data[offset] == 0.toByte() && data[offset + 1] == 0.toByte() && (data[offset + 2] == 1.toByte() || (data[offset + 2] == 0.toByte() && data[offset + 3] == 1.toByte()))

    private fun jpegOffset(data: ByteArray): Int? = (0..minOf(
        data.size - 3,
        32
    )).firstOrNull { data[it] == 0xFF.toByte() && data[it + 1] == 0xD8.toByte() && data[it + 2] == 0xFF.toByte() }

    companion object {
        private const val HEADER_SIZE = 12 // Platform UI: 8-byte timestamp + 4-byte array length.
        private const val MAX_PENDING = 64
        private val START_CODE = byteArrayOf(0, 0, 0, 1)
        private val BBB_SPS = byteArrayOf(
            0,
            0,
            0,
            1,
            0x67,
            0x64,
            0,
            0x32,
            0xAC.toByte(),
            0x72,
            0x84.toByte(),
            0x40,
            0x50,
            5,
            0xBB.toByte(),
            1,
            0x10,
            0,
            0,
            3,
            0,
            0x10,
            0,
            0,
            3,
            3,
            0xC0.toByte(),
            0xF1.toByte(),
            0x83.toByte(),
            0x18,
            0x46
        )
        private val BBB_PPS = byteArrayOf(
            0,
            0,
            0,
            1,
            0x68,
            0xE8.toByte(),
            0x43,
            0x87.toByte(),
            0x4B,
            0x22,
            0xC0.toByte()
        )
    }
}
