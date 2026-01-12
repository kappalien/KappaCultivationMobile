package com.example.kappacultivationmobile.battle.model

import com.example.kappacultivationmobile.models.Enemy

// 定義格子內可能有的內容類型
enum class CellType {
    EMPTY,          // 空白格子 (例如：草地)
    PLAYER,         // 玩家單位
    ENEMY,          // 敵人單位
    OBSTACLE,       // 障礙物 (例如：樹木，不可移動)
    HIGHLIGHT_MOVE, // 可移動範圍
    HIGHLIGHT_ATTACK // 可攻擊範圍
}

/**
 * BattleCell 類別：代表棋盤上的單個格子數據。
 * row/col 從 0 開始編號。
 */
data class BattleCell(
    val row: Int,
    val col: Int,
    var type: CellType,
    var enemy: Enemy? = null, // 如果 type 是 ENEMY，儲存敵人數據
    var playerUnitId: Int? = 1 // 玩家單位，預設 ID 為 1
)