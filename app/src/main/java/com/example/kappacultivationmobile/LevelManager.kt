package com.example.kappacultivationmobile

import android.util.Log
import com.example.kappacultivationmobile.models.LevelMilestone

class LevelManager {

    // ===========================
    // 1. 靜態資料區 (Companion Object)
    // 這裡存放全域共用的數據，讓 AssetsInit 可以直接呼叫初始化
    // ===========================
    companion object {
        private var milestones: List<LevelMilestone> = emptyList()

        // 檢查是否已經初始化
        val isInitialized: Boolean get() = milestones.isNotEmpty()

        /**
         * 由 AssetsInit 呼叫，載入靜態的里程碑數據
         */
        fun initStatsData(data: List<LevelMilestone>) {
            if (data.isEmpty()) {
                Log.e("LevelManager", "初始化失敗：傳入的數據列表為空")
                return
            }
            milestones = data.sortedBy { it.level }
            Log.d("LevelManager", "等級資料初始化成功，共載入 ${milestones.size} 筆數據")
        }
    }

    // ===========================
    // 2. 玩家狀態管理 (實體變數)
    // 每個玩家/存檔獨有的狀態
    // ===========================

    var currentLevel: Int = 1
        private set

    var currentExp: Long = 0
        private set

    private val baseExp: Long = 100

    fun getRequiredExp(): Long {
        return baseExp * (1L shl (currentLevel - 1))
    }

    fun addExp(amount: Long): Boolean {
        currentExp += amount
        var leveledUp = false

        while (currentExp >= getRequiredExp()) {
            currentExp -= getRequiredExp()
            currentLevel++
            leveledUp = true
        }
        return leveledUp
    }

    fun loadData(savedLevel: Int, savedExp: Long) {
        currentLevel = savedLevel
        currentExp = savedExp
    }

    // ===========================
    // 3. 計算邏輯
    // 使用靜態的 milestones 進行計算
    // ===========================

    fun getStatsForLevel(targetLevel: Int): LevelMilestone {
        if (!isInitialized) {
            Log.e("LevelManager", "警告：LevelManager 尚未初始化數據 (AssetsInit 未執行或失敗)")
            // 回傳保底數據避免崩潰
            return LevelMilestone(1, 100, 50, 10, 5, 100, emptyList())
        }

        // 直接存取 companion object 中的 milestones
        val lower = milestones.lastOrNull { it.level <= targetLevel } ?: milestones.first()
        val upper = milestones.firstOrNull { it.level > targetLevel } ?: lower

        if (lower.level == upper.level || lower.level == targetLevel) {
            return lower.copy(level = targetLevel)
        }

        val range = (upper.level - lower.level).toDouble()
        val progress = (targetLevel - lower.level) / range

        return LevelMilestone(
            level = targetLevel,
            health = interpolate(lower.health, upper.health, progress),
            mana = interpolate(lower.mana, upper.mana, progress),
            attack = interpolate(lower.attack, upper.attack, progress),
            defense = interpolate(lower.defense, upper.defense, progress),
            nextLevelSteps = lower.nextLevelSteps,
            skills = lower.skills
        )
    }

    private fun interpolate(start: Int, end: Int, fraction: Double): Int {
        return (start + (end - start) * fraction).toInt()
    }
}