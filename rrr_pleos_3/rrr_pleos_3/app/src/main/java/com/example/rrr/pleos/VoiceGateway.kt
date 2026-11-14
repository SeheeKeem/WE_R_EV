package com.example.rrr.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "WerEv/Voice"

interface VoiceGateway {
    val source: StateFlow<Source>
    fun start()
    fun stop()

    /**
     * 같은 key 로는 한 번만 말한다 (트립당 1회 = key 에 tripId 를 넣는다).
     * @return 이번 호출에서 실제로 말하기를 시도했으면 true, 이미 말한 key 면 false
     */
    fun speakOnce(key: String, text: String, locale: Locale): Boolean
}

/** SDK 가 없는 환경용: 소리는 나지 않고 '1회만' 규칙만 지킨다 (화면에는 같은 문장이 뜬다) */
class DemoVoiceGateway : VoiceGateway {
    private val spoken = mutableSetOf<String>()
    override val source: StateFlow<Source> = MutableStateFlow(Source.DEMO)
    override fun start() = Unit
    override fun stop() = Unit
    override fun speakOnce(key: String, text: String, locale: Locale): Boolean = spoken.add(key)
}

/** Pleos TextToSpeech SDK — ON_DEVICE 모드 (앱 등록 불필요) */
class PleosVoiceGateway(private val context: Context) : VoiceGateway {
    private val spoken = mutableSetOf<String>()
    private val _source = MutableStateFlow(Source.DEMO)
    override val source: StateFlow<Source> = _source.asStateFlow()

    private var tts: TextToSpeech? = null

    override fun start() {
        if (tts != null) stop()
        try {
            val t = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "TTS ready")
                    _source.value = Source.PLEOS
                }

                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "TTS init failed → DEMO: $error")
                    _source.value = Source.DEMO
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "TTS SDK unavailable → DEMO: $t")
            tts = null
        }
    }

    override fun stop() {
        val t = tts ?: return
        tts = null
        _source.value = Source.DEMO
        runCatching { t.release() }
    }

    override fun speakOnce(key: String, text: String, locale: Locale): Boolean {
        if (!spoken.add(key)) return false
        val t = tts
        if (t != null && _source.value == Source.PLEOS) {
            runCatching {
                t.setLocale(locale)
                // isFinal = true: 한 문장으로 끝나는 발화
                t.speak(text, true)
            }.onFailure { Log.w(TAG, "speak failed: $it") }
        }
        return true
    }
}
