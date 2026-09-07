package com.example.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

class AudioTrackPlayer(
    private val coroutineScope: CoroutineScope,
    private val sampleRate: Int = 24000
) {
    private var audioTrack: AudioTrack? = null
    private val pcmQueue = ConcurrentLinkedQueue<ByteArray>()
    private var playbackJob: Job? = null
    @Volatile
    private var isPlaying = false

    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (_: Exception) { }
    }

    /**
     * Enqueues PCM audio chunk (16-bit PCM, mono)
     */
    fun enqueueChunk(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        pcmQueue.offer(chunk)
        ensurePlaybackLoop()
    }

    private fun ensurePlaybackLoop() {
        if (isPlaying) return
        isPlaying = true
        onPlaybackStarted?.invoke()

        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    initAudioTrack()
                }
                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.play()
                }

                while (isActive && isPlaying) {
                    val chunk = pcmQueue.poll()
                    if (chunk != null) {
                        audioTrack?.write(chunk, 0, chunk.size)

                        // Calculate RMS amplitude for speaking visualization
                        var sum = 0.0
                        var i = 0
                        while (i < chunk.size - 1) {
                            val sample = (chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)
                            sum += sample * sample
                            i += 2
                        }
                        val sampleCount = chunk.size / 2
                        val rms = if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
                        val normalized = (rms / 10000.0).toFloat().coerceIn(0.1f, 1.0f)
                        onAmplitudeChanged?.invoke(normalized)
                    } else {
                        // Empty queue: brief rest before checking or finishing
                        kotlinx.coroutines.delay(20)
                        if (pcmQueue.isEmpty()) {
                            break
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                isPlaying = false
                onAmplitudeChanged?.invoke(0f)
                onPlaybackEnded?.invoke()
            }
        }
    }

    /**
     * Immediately cuts off audio playback when user interrupts or new turn starts
     */
    fun interrupt() {
        pcmQueue.clear()
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) { }

        onAmplitudeChanged?.invoke(0f)
        onPlaybackEnded?.invoke()
    }

    fun release() {
        interrupt()
        try {
            audioTrack?.release()
        } catch (_: Exception) { }
        audioTrack = null
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying
}
