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
    private var dialogStepInterval: Int = 100 // 🔹 新增：可以設定多少步顯示一次對話（預設 10 步）
) : SensorEventListener {

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            currentStepsInLevel += 1 // 增加步數
            val nextLevelSteps = levelInfoList[currentLevel - 1].nextLevelSteps // 需要的步數

            var response = "" // 🔹 預設不顯示對話
            val randomChance = (1..100).random()
            Log.d("CharacterResponse", "隨機機率: $randomChance，步數: $currentStepsInLevel，升級需求: $nextLevelSteps")

            // *隨機機率觸發
            if (randomChance <= 20) { // 20% 機率觸發對話
                response = characterResponse.getRandomResponseForSteps()
                Log.d("CharacterResponse", "觸發一般對話: $response")
            }

            // 判斷是否升級
            if (currentStepsInLevel >= nextLevelSteps) {
                currentLevel += 1 // 升級
                currentStepsInLevel = 0 // 步數歸零
                response = characterResponse.getLevelUpResponse() // 給升級回應
                Log.d("CharacterResponse", "觸發升級對話: $response")
            } else if (nextLevelSteps - currentStepsInLevel in 1..10) {
                response = characterResponse.getAlmostLevelUpResponse() // 快升級的回應
                Log.d("CharacterResponse", "觸發即將升級對話: $response")
            }

            // 更新 UI
            onStepCountChanged(currentStepsInLevel, currentLevel, response)
            Log.d("CharacterResponse", "最終發送對話到 UI: $response")

            // 存入 SharedPreferences
            with(sharedPreferences.edit()) {
                putInt("currentStepsInLevel", currentStepsInLevel)
                putInt("currentLevel", currentLevel)
                apply()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 忽略
    }
}

