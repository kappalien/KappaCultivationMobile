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
    private var dialogStepInterval: Int = 30 // 🔹 新增：可以設定多少步顯示一次對話（預設 100 步）
) : SensorEventListener {

    private var lastDialogStep = 0 // 🔹 **記錄上次顯示對話的步數**
    private var initialStepCount = sharedPreferences.getInt("initialStepCount", -1) // 🔹 記錄起始步數

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()

            // 如果還沒初始化 initialStepCount，就設置並儲存
            if (initialStepCount == -1) {
                initialStepCount = totalSteps
                sharedPreferences.edit().putInt("initialStepCount", initialStepCount).apply()
            }

            val stepsSinceStart = totalSteps - initialStepCount
            if (stepsSinceStart > currentStepsInLevel) {
                currentStepsInLevel += 1
                Log.d("StepCounter", "步數增加，目前累積: $currentStepsInLevel")

                // 儲存目前步數到 SharedPreferences
                with(sharedPreferences.edit()) {
                    putInt("currentStepsInLevel", currentStepsInLevel)
                    putInt("currentLevel", currentLevel)
                    apply()
                }

                val nextLevelSteps = levelInfoList[currentLevel - 1].nextLevelSteps

                var response = ""
                val randomChance = (1..100).random()
                Log.d("CharacterResponse", "隨機機率: $randomChance，步數: $currentStepsInLevel，升級需求: $nextLevelSteps")

                if (currentStepsInLevel >= nextLevelSteps) {
                    currentLevel++
                    currentStepsInLevel = 0

                    // 更新 initialStepCount 為目前的 sensor 值，避免升級後重複累加
                    initialStepCount = totalSteps
                    sharedPreferences.edit().putInt("initialStepCount", initialStepCount).apply()

                    with(sharedPreferences.edit()) {
                        putInt("currentStepsInLevel", currentStepsInLevel)
                        putInt("currentLevel", currentLevel)
                        apply()
                    }

                    response = characterResponse.getLevelUpResponse()
                    Log.d("LevelUp", "升級到等級 $currentLevel")
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
                Log.d("CharacterResponse", "最終發送對話到 UI: $response")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 忽略
    }
}
