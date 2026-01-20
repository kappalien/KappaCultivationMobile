package com.example.kappacultivationmobile

object BattleDamageLogic {

    // 計算傷害公式 (保持不變)
    fun calculateDamage(attackerAtk: Int, defenderDef: Int): Int {
        val base = attackerAtk - defenderDef
        // 傷害至少為 1，並加上 0~3 的隨機浮動值
        return if (base < 1) 1 else base + (0..3).random()
    }

    fun calculateSkillDamage(attackerAtk: Int, defenderDef: Int, multiplier: Double): Int {
        // 先算基礎傷害
        val baseDamage = calculateDamage(attackerAtk, defenderDef)

        // 再乘上技能倍率 (取整數)
        return (baseDamage * multiplier).toInt()
    }

}