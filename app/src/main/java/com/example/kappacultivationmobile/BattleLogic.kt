package com.example.kappacultivationmobile

import com.example.kappacultivationmobile.models.Enemy
import com.example.kappacultivationmobile.models.LevelMilestone // ✅ 新增：引入正確的數值模型

object BattleLogic {

    // 計算傷害公式 (保持不變)
    fun calculateDamage(attackerAtk: Int, defenderDef: Int): Int {
        val base = attackerAtk - defenderDef
        // 傷害至少為 1，並加上 0~3 的隨機浮動值
        return if (base < 1) 1 else base + (0..3).random()
    }

    // ✅ 修改：將 player 的型別從 LevelInfo 改為 LevelMilestone
    fun performAttack(player: LevelMilestone, enemy: Enemy): Pair<Int, Int> {
        val playerDamage = calculateDamage(player.attack, enemy.defense)
        val enemyDamage = calculateDamage(enemy.attack, player.defense)
        return Pair(playerDamage, enemyDamage)
    }
}