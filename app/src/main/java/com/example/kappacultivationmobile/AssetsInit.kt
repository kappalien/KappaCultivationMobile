package com.example.kappacultivationmobile

import android.app.Application
import android.util.Log
import com.example.kappacultivationmobile.models.LevelMilestone
import com.example.kappacultivationmobile.models.Enemy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AssetsInit : Application() {
    override fun onCreate() {
        super.onCreate()
        val gson = Gson()
        try {
            // 1. 初始化等級計算機 (LevelMilestone)
            val levelJson = assets.open("level_info.json").bufferedReader().use { it.readText() }
            val levelData: List<LevelMilestone> = gson.fromJson(levelJson, object : TypeToken<List<LevelMilestone>>() {}.type)
            LevelCalculator.init(levelData)

            // 2. 初始化敵人管理器 (EnemyManager)
            val enemyJson = assets.open("enemies.json").bufferedReader().use { it.readText() }
            val enemyData: List<Enemy> = gson.fromJson(enemyJson, object : TypeToken<List<Enemy>>() {}.type)
            EnemyManager.init(enemyData)

            Log.d("AssetsInit", "所有資產初始化成功！")
        } catch (e: Exception) {
            Log.e("AssetsInit", "初始化失敗: ${e.message}")
        }
    }
}