package com.example.kappacultivationmobile

import com.example.kappacultivationmobile.models.Enemy // 確保路徑指向你的 Enemy 模型
import android.util.Log

object EnemyManager {
    // 存放所有敵人的私有列表
    private var enemies: List<Enemy> = emptyList()

    /**
     * 由 AssetsInit 呼叫，初始化敵人數據
     */
    fun init(data: List<Enemy>) {
        enemies = data
        Log.d("EnemyManager", "敵人數據已初始化，總數: ${enemies.size}")
    }

    /**
     * 隨機獲取一個敵人（用於戰鬥啟動）
     */
    fun getRandomEnemy(): Enemy? {
        if (enemies.isEmpty()) {
            Log.e("EnemyManager", "錯誤：尚未初始化敵人數據或 JSON 為空！")
            return null
        }
        return enemies.random()
    }

    /**
     * 獲取所有敵人列表（未來用於圖鑑功能）
     */
    fun getAllEnemies(): List<Enemy> = enemies
}