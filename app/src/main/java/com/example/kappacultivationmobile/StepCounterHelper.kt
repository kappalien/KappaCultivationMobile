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
    private var dialogStepInterval: Int = 100 // 🔹 新增：可以設定多少步顯示一次對話（預設 100 步）
) : SensorEventListener {

    private var lastDialogStep = 0 // 🔹 **記錄上次顯示對話的步數**

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            currentStepsInLevel += 1
            val nextLevelSteps = levelInfoList[currentLevel - 1].nextLevelSteps

            var response = ""
            val randomChance = (1..100).random()
            Log.d("CharacterResponse", "隨機機率: $randomChance，步數: $currentStepsInLevel，升級需求: $nextLevelSteps")

            // **優先處理升級對話**
            if (currentStepsInLevel >= nextLevelSteps) {
                response = characterResponse.getLevelUpResponse()
            } else if (nextLevelSteps - currentStepsInLevel in 1..10) {
                response = characterResponse.getAlmostLevelUpResponse()
            } else {
                // **確保對話不會在短時間內過度觸發**
                if (currentStepsInLevel - lastDialogStep >= dialogStepInterval) {
                    if (randomChance <= 20) {
                        response = characterResponse.getRandomResponseForSteps()
                        lastDialogStep = currentStepsInLevel // 記錄上次顯示對話的步數
                    }
                }
            }

            // **確保 UI 及時更新**
            onStepCountChanged(currentStepsInLevel, currentLevel, response)
            Log.d("CharacterResponse", "最終發送對話到 UI: $response")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 忽略
    }
}

