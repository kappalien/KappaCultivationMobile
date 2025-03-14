package com.example.kappacultivationmobile

import android.os.Handler
import android.os.Looper

class PetUpdateManager(
    private val petStatus: PetStatus,
    private val updateUI: () -> Unit // 更新 UI
) {
    private val handler = Handler(Looper.getMainLooper())

    private val statusUpdater = object : Runnable {
        override fun run() {
            petStatus.decreaseHunger()
            petStatus.decreaseEnergy()
            petStatus.decreaseMood()
            petStatus.decreaseCleanliness()

            updateUI()
            handler.postDelayed(this, 60000) // 每 60 秒更新
        }
    }

    fun startUpdating() {
        handler.post(statusUpdater)
    }

    fun stopUpdating() {
        handler.removeCallbacks(statusUpdater)
    }
}
