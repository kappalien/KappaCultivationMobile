package com.example.kappacultivationmobile

object LevelCalculator {
    private var milestones: List<LevelMilestone> = emptyList()

    fun init(data: List<LevelMilestone>) {
        milestones = data.sortedBy { it.level }
    }

    fun getStatsForLevel(targetLevel: Int): LevelMilestone {
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