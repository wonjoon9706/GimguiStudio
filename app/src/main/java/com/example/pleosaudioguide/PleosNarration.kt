package com.example.pleosaudioguide

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

/** 표준 Android TTS가 없는 Pleos의 시연 원고 재생용. AI 생성이나 서버 등록을 사용하지 않는다. */
class PleosNarration(context: Context, private val onState: (String) -> Unit,
    private val onReady: () -> Unit, private val onStarted: () -> Unit,
    private val onFinished: (Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private val engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val startTimeout = Runnable {
        onState("Pleos 음성 시작 응답 없음")
        engine.stop()
        onFinished(false)
    }
    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = dispatch {
            main.removeCallbacks(startTimeout)
            Log.i("AudioGuideVoice", "Playback started")
            onStarted()
        }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = dispatch {
            main.removeCallbacks(startTimeout)
            Log.i("AudioGuideVoice", "Playback completed")
            onFinished(true)
        }
        override fun onStop() = dispatch {
            main.removeCallbacks(startTimeout)
            Log.i("AudioGuideVoice", "Playback stopped")
            onFinished(false)
        }
        override fun onError(errorType: ErrorType) = dispatch {
            main.removeCallbacks(startTimeout)
            onState("Pleos 음성 재생 오류: $errorType")
            onFinished(false)
        }
    }

    init {
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() = dispatch {
                engine.addEventListener(listener)
                engine.setLocale(Locale.KOREA)
                onState("Pleos 기기 내 시연 음성 준비 완료")
                onReady()
            }
            override fun onFailure(errorType: ErrorType) = dispatch {
                onState("Pleos 음성 초기화 실패: $errorType")
            }
        })
    }

    fun speak(content: AudioContent) {
        main.postDelayed(startTimeout, 15000)
        try { engine.speak(content.script) }
        catch (error: Exception) {
            main.removeCallbacks(startTimeout)
            onState("Pleos 음성 요청 실패: ${error.message}")
            onFinished(false)
        }
    }

    fun stop() { engine.stop() }
    private fun dispatch(action: () -> Unit) { main.post { if (!closed) action() } }
    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        engine.stop()
        engine.removeEventListener(listener)
        engine.release()
    }
}
