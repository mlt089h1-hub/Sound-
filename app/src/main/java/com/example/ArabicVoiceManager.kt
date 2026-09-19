package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sin

class ArabicVoiceManager(
    private val context: Context,
    private val onInitComplete: (isReady: Boolean, hasArabicOffline: Boolean) -> Unit,
    private val onSpeechStatusChanged: (isSpeaking: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    var isArabicSupportedByTts: Boolean = false
        private set
    var isTtsReady: Boolean = false
        private set
    var useOnlineEngine: Boolean = true

    private var mediaPlayer: MediaPlayer? = null
    private val audioCacheDir = File(context.cacheDir, "tts_cache").apply { mkdirs() }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val arLocale = Locale("ar")
            val langResult = tts?.setLanguage(arLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            isArabicSupportedByTts = (langResult != TextToSpeech.LANG_MISSING_DATA && langResult != TextToSpeech.LANG_NOT_SUPPORTED)

            if (!isArabicSupportedByTts) {
                // Try Arabic Egyptian / Saudi specifically
                val arEG = Locale("ar", "EG")
                val arSA = Locale("ar", "SA")
                val egResult = tts?.setLanguage(arEG)
                val saResult = tts?.setLanguage(arSA)
                isArabicSupportedByTts = (egResult != TextToSpeech.LANG_MISSING_DATA && egResult != TextToSpeech.LANG_NOT_SUPPORTED) ||
                                         (saResult != TextToSpeech.LANG_MISSING_DATA && saResult != TextToSpeech.LANG_NOT_SUPPORTED)
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeechStatusChanged(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeechStatusChanged(false)
                }

                override fun onError(utteranceId: String?) {
                    Log.e("ArabicVoiceManager", "TTS error on utterance: $utteranceId")
                    onSpeechStatusChanged(false)
                }
            })

            isTtsReady = true
            onInitComplete(true, isArabicSupportedByTts)
        } else {
            isTtsReady = false
            onInitComplete(false, false)
        }
    }

    suspend fun announce(
        text: String,
        persona: ArabicVoicePersona,
        speechRate: Float,
        volume: Float,
        repeatTwice: Boolean,
        playChime: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            onSpeechStatusChanged(true)

            if (playChime) {
                playMedicalChimeTone(volume)
                Thread.sleep(180)
            }

            // Calculate composite effective rate and pitch
            val effectiveRate = (speechRate * persona.engineRateModifier).coerceIn(0.6f, 1.8f)
            val effectivePitch = persona.pitch.coerceIn(0.7f, 1.8f)

            var spokenSuccessfully = false

            // Try online natural Arabic pronunciation if enabled
            if (useOnlineEngine) {
                spokenSuccessfully = playOnlineArabicAudio(text, volume, effectiveRate, effectivePitch)
            }

            // Fallback to local device TTS
            if (!spokenSuccessfully) {
                speakViaAndroidTts(text, effectiveRate, effectivePitch, volume)
            }

            if (repeatTwice) {
                Thread.sleep(2500)
                if (useOnlineEngine) {
                    val repeated = playOnlineArabicAudio(text, volume, effectiveRate, effectivePitch)
                    if (!repeated) {
                        speakViaAndroidTts(text, effectiveRate, effectivePitch, volume)
                    }
                } else {
                    speakViaAndroidTts(text, effectiveRate, effectivePitch, volume)
                }
            }
        } catch (e: Exception) {
            Log.e("ArabicVoiceManager", "Error in announce", e)
        } finally {
            Thread.sleep(500)
            onSpeechStatusChanged(false)
        }
    }

    private fun playOnlineArabicAudio(
        text: String,
        volume: Float,
        playbackSpeed: Float,
        playbackPitch: Float
    ): Boolean {
        return try {
            val cacheKey = "ar_" + text.hashCode().toString() + ".mp3"
            val cachedFile = File(audioCacheDir, cacheKey)

            if (!cachedFile.exists() || cachedFile.length() == 0L) {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val streamUrl = "https://translate.google.com/translate_tts?ie=UTF-8&tl=ar&client=tw-ob&q=$encoded"
                val url = URL(streamUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { input ->
                        FileOutputStream(cachedFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    return false
                }
            }

            if (cachedFile.exists() && cachedFile.length() > 500L) {
                playAudioFileBlocking(cachedFile, volume, playbackSpeed, playbackPitch)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w("ArabicVoiceManager", "Online TTS failed, falling back to local TTS: ${e.message}")
            false
        }
    }

    private fun playAudioFileBlocking(
        file: File,
        volume: Float,
        speed: Float,
        pitch: Float
    ) {
        val lock = Object()
        var completed = false

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
            setDataSource(file.absolutePath)
            setVolume(volume, volume)

            setOnCompletionListener {
                synchronized(lock) {
                    completed = true
                    lock.notifyAll()
                }
            }
            setOnErrorListener { _, _, _ ->
                synchronized(lock) {
                    completed = true
                    lock.notifyAll()
                }
                true
            }
            prepare()

            // Apply Pitch and Speed tuning to create distinct voice personas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    playbackParams = PlaybackParams().apply {
                        this.speed = speed.coerceIn(0.5f, 2.0f)
                        this.pitch = pitch.coerceIn(0.7f, 1.8f)
                    }
                } catch (e: Exception) {
                    Log.w("ArabicVoiceManager", "Could not set PlaybackParams", e)
                }
            }

            start()
        }

        synchronized(lock) {
            val startWait = System.currentTimeMillis()
            while (!completed && (System.currentTimeMillis() - startWait < 15000)) {
                try {
                    lock.wait(1000)
                } catch (ignored: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun speakViaAndroidTts(
        text: String,
        speechRate: Float,
        pitch: Float,
        volume: Float
    ) {
        tts?.let { engine ->
            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            }
            val utteranceId = "ann_" + System.currentTimeMillis()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            val estimatedDuration = ((text.length * 90) / speechRate).toLong().coerceIn(2000L, 8000L)
            Thread.sleep(estimatedDuration)
        }
    }

    private fun playMedicalChimeTone(volume: Float) {
        val sampleRate = 44100
        val duration = 0.28
        // Hospital two-tone chime: D5 (587.33 Hz) and A5 (880.00 Hz)
        val frequencies = doubleArrayOf(587.33, 880.00)
        val audioSamples = mutableListOf<Short>()

        for (freq in frequencies) {
            val numSamples = (sampleRate * duration).toInt()
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val attack = (t / 0.02).coerceAtMost(1.0)
                val decay = exp(-3.8 * (t / (duration + 0.1)))
                val env = attack * decay
                val sample = (env * sin(2.0 * Math.PI * freq * t) * 28000 * volume).toInt().coerceIn(-32768, 32767).toShort()
                audioSamples.add(sample)
            }
        }

        val shortArray = audioSamples.toShortArray()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(shortArray.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(shortArray, 0, shortArray.size)
        track.play()
        Thread.sleep(600)
        track.release()
    }

    fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            tts?.stop()
            tts?.shutdown()
        } catch (ignored: Exception) {}
    }
}
