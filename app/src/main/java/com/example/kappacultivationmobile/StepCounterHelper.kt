package com.example.kappacultivationmobile

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.content.SharedPreferences
import android.util.Log

class StepCounterHelper(
    private val onStepCountChanged: (Int, Int, String) -> Unit, // UI 更新函數
    private val levelManager: LevelManager,
    private val sharedPreferences: SharedPreferences,
    private val characterResponse: CharacterResponse,
    private var dialogStepInterval: Int = 30,
    private val petStatus: PetStatus
) : SensorEventListener {

    private var lastDialogStep = 0L
    private var energyRestoreAccumulator = 0

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt() // 裝置開機後累計總步數
            Log.d("StepSensor", "✅ 收到 TYPE_STEP_COUNTER 感應器值: $totalSteps")

            // 初始化或校正 initialStepCount
            val hasInitial = sharedPreferences.contains("initialStepCount")
            val storedInitial = sharedPreferences.getInt("initialStepCount", totalSteps)

            if (!hasInitial || storedInitial > totalSteps) {
                sharedPreferences.edit().putInt("initialStepCount", totalSteps).apply()
                Log.d("StepSensor", "⚠️ 自動重設 initialStepCount 為 $totalSteps")
            }

            val initialStepCount = sharedPreferences.getInt("initialStepCount", totalSteps)

            // 計算「本等級」已走的步數
            val stepsSinceStart = totalSteps - initialStepCount

            // 取得 LevelManager 目前的經驗值
            val currentExp = levelManager.currentExp

            Log.d("StepSensor", "計算步數: totalSteps=$totalSteps, initial=$initialStepCount, 差值=$stepsSinceStart, ManagerExp=$currentExp")

            // 若感應器的步數 > Manager 紀錄的經驗值，代表有新步數
            var diff = stepsSinceStart - currentExp

            if (diff > 0) {
                var anyLevelUp = false // 標記這批步數中是否發生過升級

                // === 迴圈開始：補足所有漏掉的步數 ===
                while (diff > 0) {
                    val isLevelUp = levelManager.addExp(1)
                    if (isLevelUp) {
                        anyLevelUp = true
                    }

                    energyRestoreAccumulator += 1

                    // 1. 處理回血 (每 20 步)
                    val stats = levelManager.getStatsForLevel(levelManager.currentLevel)
                    val maxHp = stats.health
                    val currentHp = sharedPreferences.getInt("currentHp", maxHp)

                    if (energyRestoreAccumulator >= 20 && currentHp < maxHp) {
                        val newHp = (currentHp + 5).coerceAtMost(maxHp)
                        sharedPreferences.edit().putInt("currentHp", newHp).apply()
                        energyRestoreAccumulator = 0
                    }

                    // 2. 處理能量回復
                    petStatus.energy = (petStatus.energy + 1).coerceAtMost(100)

                    diff-- // 扣除已處理的步數
                }

                // 3. 處理對話與升級回應
                var response = ""

                if (anyLevelUp) {
                    // 🎉 升級了！
                    // 重設硬體感應器的基準點，因為 LevelManager 的 exp 已歸零
                    sharedPreferences.edit().putInt("initialStepCount", totalSteps).apply()

                    // 儲存歸零後的狀態 (Exp=0)
                    with(sharedPreferences.edit()) {
                        putLong("currentExp", 0L)
                        putInt("currentStepsInLevel", 0)
                        putInt("currentLevel", levelManager.currentLevel)
                        apply()
                    }

                    response = characterResponse.getLevelUpResponse()
                    lastDialogStep = 0 // 重置對話計數
                } else {
                    // 沒升級，僅儲存當前經驗值
                    with(sharedPreferences.edit()) {
                        putLong("currentExp", levelManager.currentExp)
                        putInt("currentLevel", levelManager.currentLevel)
                        putInt("currentStepsInLevel", levelManager.currentExp.toInt())
                        apply()
                    }

                    // 檢查是否快升級
                    val requiredExp = levelManager.getRequiredExp()
                    val remainingExp = requiredExp - levelManager.currentExp

                    if (remainingExp in 1..10) {
                        response = characterResponse.getAlmostLevelUpResponse()
                    } else {
                        // 一般對話邏輯
                        if (levelManager.currentExp - lastDialogStep >= dialogStepInterval) {
                            if ((1..100).random() <= 20) {
                                response = characterResponse.getRandomResponseForSteps()
                                lastDialogStep = levelManager.currentExp
                            }
                        }
                    }
                }

                // 4. 更新 UI
                onStepCountChanged(levelManager.currentExp.toInt(), levelManager.currentLevel, response)
            }

            // 總步數累計 (全域統計)
            val totalStepsSoFar = sharedPreferences.getInt("steps_total", 0)
            sharedPreferences.edit().putInt("steps_total", totalStepsSoFar + 1).apply()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 這裡不需要實作，但必須保留空殼
    }
}