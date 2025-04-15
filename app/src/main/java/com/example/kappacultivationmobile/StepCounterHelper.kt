package com.example.kappacultivationmobile

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.content.SharedPreferences
import android.util.Log

class StepCounterHelper(
    private var currentStepsInLevel: Int, // 當前等級內的步數
    private var currentLevel: Int, // 當前等級
    private val onStepCountChanged: (Int, Int, String) -> Unit, // UI 更新函數
    private val levelInfoList: List<LevelInfo>,
    private val sharedPreferences: SharedPreferences,
    private val characterResponse: CharacterResponse, // 角色回應
    private var dialogStepInterval: Int = 30, // 🔹 新增：可以設定多少步顯示一次對話（預設 30 步）
    private val petStatus: PetStatus // ✅ 新增：傳入電子雞狀態
) : SensorEventListener {

    private var lastDialogStep = 0 // 🔹 **記錄上次顯示對話的步數**
    private var initialStepCount = sharedPreferences.getInt("initialStepCount", -1) // 🔹 記錄起始步數
    private var energyRestoreAccumulator = 0 // ✅ 新增：累積未處理步數

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()

            if (initialStepCount == -1) {
                initialStepCount = totalSteps
                sharedPreferences.edit().putInt("initialStepCount", initialStepCount).apply()
            }

            val stepsSinceStart = totalSteps - initialStepCount
            if (stepsSinceStart > currentStepsInLevel) {
                currentStepsInLevel += 1
                energyRestoreAccumulator += 1 // ✅ 每步都加進累計器

                // ✅ 每走一步就恢復 1 能量
                petStatus.energy = (petStatus.energy + 1).coerceAtMost(100)
                Log.d("PetStatus", "步數回復能量：目前能量 ${petStatus.energy}")

                with(sharedPreferences.edit()) {
                    putInt("currentStepsInLevel", currentStepsInLevel)
                    putInt("currentLevel", currentLevel)
                    apply()
                }

                val nextLevelSteps = levelInfoList[currentLevel - 1].nextLevelSteps

                var response = ""
                val randomChance = (1..100).random()

                if (currentStepsInLevel >= nextLevelSteps) {
                    currentLevel++
                    currentStepsInLevel = 0
                    initialStepCount = totalSteps
                    sharedPreferences.edit().putInt("initialStepCount", initialStepCount).apply()

                    with(sharedPreferences.edit()) {
                        putInt("currentStepsInLevel", currentStepsInLevel)
                        putInt("currentLevel", currentLevel)
                        apply()
                    }

                    response = characterResponse.getLevelUpResponse()
                } else if (nextLevelSteps - currentStepsInLevel in 1..10) {
                    response = characterResponse.getAlmostLevelUpResponse()
                } else {
                    if (currentStepsInLevel - lastDialogStep >= dialogStepInterval) {
                        if (randomChance <= 20) {
                            response = characterResponse.getRandomResponseForSteps()
                            lastDialogStep = currentStepsInLevel
                        }
                    }
                }

                onStepCountChanged(currentStepsInLevel, currentLevel, response)
            }
        }
        val totalStepsSoFar = sharedPreferences.getInt("steps_total", 0)
        sharedPreferences.edit().putInt("steps_total", totalStepsSoFar + 1).apply()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 忽略
    }
}