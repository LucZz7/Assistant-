package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioRecordManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val sampleRate: Int = 16000
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile
    private var isRecording = false

    var onAudioChunk: ((ByteArray) -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (!hasPermission || isRecording) return false

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        val chunk = buffer.copyOf(bytesRead)
                        onAudioChunk?.invoke(chunk)

                        // Calculate normalized RMS amplitude (0.0 to 1.0)
                        var sum = 0.0
                        var i = 0
                        while (i < bytesRead - 1) {
                            val sample = (chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)
                            sum += sample * sample
                            i += 2
                        }
                        val sampleCount = bytesRead / 2
                        val rms = if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
                        val normalized = (rms / 12000.0).toFloat().coerceIn(0.05f, 1.0f)
                        onAmplitudeChanged?.invoke(normalized)
                    }
                }
            }
            return true
        } catch (_: Exception) {
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (_: Exception) { }
        audioRecord = null
        onAmplitudeChanged?.invoke(0f)
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}
