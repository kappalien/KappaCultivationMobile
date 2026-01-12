package com.example.kappacultivationmobile

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlin.random.Random

object EffectSoundManager {
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<String, Int>()
    private val attackSounds = listOf("sword", "punch")

    fun init(context: Context) {
        if (soundPool != null) return

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()

        soundMap["sword"] = soundPool!!.load(context, R.raw.sword_slash, 1)
        soundMap["punch"] = soundPool!!.load(context, R.raw.punch_hit, 1)

        Log.d("EffectSoundManager", "🎧 音效載入完成：${soundMap.keys}")
    }

    fun play(name: String) {
        val id = soundMap[name]
        if (id != null) {
            soundPool?.play(id, 1.8f, 1.8f, 1, 0, 1f)
        } else {
            Log.w("EffectSoundManager", "⚠️ 找不到音效: $name")
        }
    }

    fun playRandomAttack() {
        val key = attackSounds.random()
        play(key)
        Log.d("EffectSoundManager", "🔊 播放攻擊音效: $key")
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
