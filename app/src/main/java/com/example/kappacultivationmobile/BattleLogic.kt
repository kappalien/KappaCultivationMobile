package com.example.kappacultivationmobile

import com.example.kappacultivationmobile.model.Enemy


object BattleLogic {
    fun calculateDamage(attackerAtk: Int, defenderDef: Int): Int {
        val base = attackerAtk - defenderDef
        return if (base < 1) 1 else base + (0..3).random()
    }

    fun performAttack(player: LevelInfo, enemy: Enemy): Pair<Int, Int> {
        val playerDamage = calculateDamage(player.attack, enemy.defense)
        val enemyDamage = calculateDamage(enemy.attack, player.defense)
        return Pair(playerDamage, enemyDamage)
    }
}