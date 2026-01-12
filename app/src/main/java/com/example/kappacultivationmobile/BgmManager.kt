package com.example.kappacultivationmobile

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * 背景音樂統一管理器，支援多場景切換與重複播放控制
 */
object BgmManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentResId: Int? = null
    private var currentOwner: String? = null

    /**
     * 播放指定資源 ID 的音樂（若來源相同且已在播放，則略過）
     */
    fun play(context: Context, resId: Int, owner: String = "") {
        if (currentResId == resId && mediaPlayer?.isPlaying == true && currentOwner == owner) {
            Log.d("BGM", "🎵 同一來源 $owner 播放相同音樂，略過")
            return
        }

        stop() // 停止原有音樂（不論來源）

        try {
            val afd = context.resources.openRawResourceFd(resId) ?: return
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                prepare()
                start()
            }
            currentResId = resId
            currentOwner = owner
            Log.d("BGM", "🎵 $owner 開始播放音樂 resId=$resId")
        } catch (e: Exception) {
            Log.e("BGM", "❌ 播放失敗: ${e.message}")
        }
    }

    /** 暫停當前播放（不釋放資源） */
    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            Log.d("BGM", "⏸ 暫停播放 ($currentOwner)")
        }
    }

    /** 回復播放（若已初始化） */
    fun resume() {
        if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
            mediaPlayer?.start()
            Log.d("BGM", "▶️ 繼續播放 ($currentOwner)")
        }
    }

    /** 停止並釋放播放器 */
    fun stop() {
        mediaPlayer?.let {
            it.stop()
            it.release()
            Log.d("BGM", "🛑 停止並釋放播放器 ($currentOwner)")
        }
        mediaPlayer = null
        currentResId = null
        currentOwner = null
    }

    /** 是否正在播放 */
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    /** 是否已初始化 */
    fun isInitialized(): Boolean = mediaPlayer != null

    /** 回傳當前音樂控制者 */
    fun getCurrentOwner(): String? = currentOwner
}