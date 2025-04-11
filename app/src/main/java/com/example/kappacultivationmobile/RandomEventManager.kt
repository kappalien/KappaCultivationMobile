package com.example.kappacultivationmobile

import android.os.Handler
import android.os.Looper

class RandomEventManager(
    private val triggerEventUI: (String) -> Unit // UI 更新函數
) {
    private val handler = Handler(Looper.getMainLooper())
    private val eventQueue = mutableListOf<String>() // 事件列表

    private val eventRunner = object : Runnable {
        override fun run() {
            val randomChance = (1..100).random()
            val event = when {
//                randomChance < 20 -> "遭遇敵人！⚔"
//                randomChance < 45 -> "發現靈草 🌿"
//                randomChance < 65 -> "找到寶藏 💎"
//                randomChance < 80 -> "遇見修仙 NPC 🧙"
                randomChance < 30 -> "發現靈草 🌿"
                randomChance < 60 -> "找到寶藏 💎"
                else -> null // 20% 無事件
            }

            event?.let {
                eventQueue.add(it) // 加入事件列表
                triggerEventUI(it) // 顯示事件通知
            }

            handler.postDelayed(this, (300_000..600_000).random().toLong()) // 5~10 分鐘 秒觸發一次
        }
    }

    fun startEventLoop() {
        handler.post(eventRunner) // 啟動事件循環
    }

    fun stopEventLoop() {
        handler.removeCallbacks(eventRunner) // 停止觸發新的事件
    }

    fun getEvents(): List<String> {
        return eventQueue.toList() // 確保回傳的是不可變列表
    }

    fun removeEvent(event: String) {
        eventQueue.remove(event) // **移除特定事件**
    }

    fun clearEvents() {
        if (eventQueue.isEmpty()) {
            triggerEventUI("") // **當事件已清空時，隱藏 UI**
        }
    }
}
