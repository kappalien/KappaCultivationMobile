package com.example.kappacultivationmobile

object BattleDamageLogic {

    // 基礎波動計算 (私有)
    private fun getRandomFactor(): Int = (0..3).random()

    // 舊的公式 (如果還有其他地方用到，可以保留，或者讓它呼叫新的)
    fun calculateDamage(attackerAtk: Int, defenderDef: Int): Int {
        return calculateFinalDamage(attackerAtk, defenderDef)
    }

    fun calculateSkillDamage(attackerAtk: Int, defenderDef: Int, multiplier: Double): Int {
        return calculateFinalDamage(attackerAtk, defenderDef, multiplier)
    }

    /**
     * ✅ 新增：核心傷害公式 (包含防禦減免邏輯)
     * 公式：(攻擊力 * 倍率 * 防禦減傷) + 隨機波動
     */
    fun calculateFinalDamage(atk: Int, def: Int, skillMultiplier: Double = 1.0): Int {
        // 1. 防禦減免率 (例如 def=50 -> 減傷係數 0.66)
        val defenseMultiplier = 100.0 / (100.0 + def)

        // 2. 計算傷害
        val rawDamage = (atk * skillMultiplier * defenseMultiplier).toInt()

        // 3. 加上波動並確保至少為 1
        return (rawDamage + getRandomFactor()).coerceAtLeast(1)
    }
}