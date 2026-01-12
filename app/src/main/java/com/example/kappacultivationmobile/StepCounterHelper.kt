package com.example.kappacultivationmobile

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.content.SharedPreferences
import android.util.Log

class StepCounterHelper(
    private var currentStepsInLevel: Int, // 當前等級內的步數
    private var currentLevel: Int, // 當前等級
    private val onStepCountChanged: (Int, Int, String) -> Unit, // UI 更新函數（步數、等級、對話）
    private val levelInfoList: List<LevelInfo>, // 等級資訊表
    private val sharedPreferences: SharedPreferences, // 用來儲存與讀取狀態
    private val characterResponse: CharacterResponse, // 角色的回應邏輯
    private var dialogStepInterval: Int = 30, // 每多少步可能觸發角色回應
    private val petStatus: PetStatus // 電子雞狀態（用於能量恢復）
) : SensorEventListener {

    private var lastDialogStep = 0 // 上次觸發角色回應的步數
    private var energyRestoreAccumulator = 0 // 用於記錄累積的步數來觸發回血

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt() // 裝置開機後累計總步數
            Log.d("StepSensor", "✅ 收到 TYPE_STEP_COUNTER 感應器值: $totalSteps")

            // 如果尚未記錄初始步數，則設定為目前值
            val hasInitial = sharedPreferences.contains("initialStepCount")
            val storedInitial = sharedPreferences.getInt("initialStepCount", totalSteps)

            if (!hasInitial || storedInitial > totalSteps) {
                sharedPreferences.edit().putInt("initialStepCount", totalSteps).apply()
                Log.d("StepSensor", "⚠️ 自動重設 initialStepCount 為 $totalSteps（初次 or 異常）")
            }

            val initialStepCount = sharedPreferences.getInt("initialStepCount", totalSteps)
            val stepsSinceStart = totalSteps - initialStepCount

            Log.d("StepSensor", "計算步數: totalSteps=$totalSteps, initial=$initialStepCount, 差值=$stepsSinceStart")

            // 若有新步數產生
            if (stepsSinceStart > currentStepsInLevel) {
                currentStepsInLevel += 1
                Log.d("StepDebug", "✅ 有新步數，currentStepsInLevel: $currentStepsInLevel")
                energyRestoreAccumulator += 1

                // 處理回血（每 20 步恢復 5 點）
                val levelInfo = levelInfoList[currentLevel - 1]
                val maxHp = levelInfo.health
                val currentHp = sharedPreferences.getInt("currentHp", maxHp)
                if (energyRestoreAccumulator >= 20 && currentHp < maxHp) {
                    val newHp = (currentHp + 5).coerceAtMost(maxHp)
                    sharedPreferences.edit().putInt("currentHp", newHp).apply()
                    energyRestoreAccumulator = 0
                    Log.d("StepRecovery", "步數回血：$currentHp → $newHp")
                } else {
                    Log.d("StepDebug", "❌ 沒有新步數 (stepsSinceStart: $stepsSinceStart, currentStepsInLevel: $currentStepsInLevel)")
                }

                // 每一步回復能量（上限 100）
                petStatus.energy = (petStatus.energy + 1).coerceAtMost(100)
                Log.d("PetStatus", "步數回復能量：目前能量 ${petStatus.energy}")

                // 儲存目前等級與步數
                with(sharedPreferences.edit()) {
                    putInt("currentStepsInLevel", currentStepsInLevel)
                    putInt("currentLevel", currentLevel)
                    apply()
                }

                // 處理升級與角色對話回應
                val nextLevelSteps = levelInfoList[currentLevel - 1].nextLevelSteps
                var response = ""
                val randomChance = (1..100).random()

                if (currentStepsInLevel >= nextLevelSteps) {
                    // 升級條件達成
                    currentLevel++
                    currentStepsInLevel = 0
                    sharedPreferences.edit().putInt("initialStepCount", totalSteps).apply()

                    with(sharedPreferences.edit()) {
                        putInt("currentStepsInLevel", currentStepsInLevel)
                        putInt("currentLevel", currentLevel)
                        apply()
                    }

                    response = characterResponse.getLevelUpResponse()
                } else if (nextLevelSteps - currentStepsInLevel in 1..10) {
                    // 快升級時的提示語
                    response = characterResponse.getAlmostLevelUpResponse()
                } else {
                    // 一般情況，依據間隔與隨機機率觸發對話
                    if (currentStepsInLevel - lastDialogStep >= dialogStepInterval) {
                        if (randomChance <= 20) {
                            response = characterResponse.getRandomResponseForSteps()
                            lastDialogStep = currentStepsInLevel
                        }
                    }
                }

                // 更新主畫面 UI
                onStepCountChanged(currentStepsInLevel, currentLevel, response)

                // 除錯：印出目前統計
                Log.d("StepDebug", "總步數: $totalSteps, 初始: $initialStepCount, 當前等級步數: $currentStepsInLevel")
            }

            // 總步數記錄（所有步數都會增加一次）
            val totalStepsSoFar = sharedPreferences.getInt("steps_total", 0)
            sharedPreferences.edit().putInt("steps_total", totalStepsSoFar + 1).apply()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不處理感應器精度變化
    }
}