package com.example.kappacultivationmobile
import android.util.Log
import com.example.kappacultivationmobile.models.LevelMilestone

object LevelCalculator {
    private var milestones: List<LevelMilestone> = emptyList()

    // 新增：檢查是否已經初始化
    val isInitialized: Boolean get() = milestones.isNotEmpty()

    fun init(data: List<LevelMilestone>) {
        if (data.isEmpty()) {
            Log.e("LevelCalculator", "初始化失敗：傳入的數據列表為空")
            return
        }
        milestones = data.sortedBy { it.level }
        Log.d("LevelCalculator", "初始化成功，共有 ${milestones.size} 筆里程碑數據")
    }

    fun getStatsForLevel(targetLevel: Int): LevelMilestone {
        // 防錯：如果未初始化，直接回傳一個預設的里程碑避免閃退
        if (!isInitialized) {
            Log.e("LevelCalculator", "錯誤：尚未初始化就呼叫了 getStatsForLevel！")
            // 回傳一個 Lv.1 的保底數據
            return LevelMilestone(1, 100, 50, 10, 5, 100, emptyList())
        }
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
            skills = lower.skills // 沿用該區間起始解鎖的技能
        )
    }

    private fun interpolate(start: Int, end: Int, fraction: Double): Int {
        return (start + (end - start) * fraction).toInt()
    }
}