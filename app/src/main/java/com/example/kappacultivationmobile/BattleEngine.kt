package com.example.kappacultivationmobile.battle

import com.example.kappacultivationmobile.battle.model.BattleCell
import com.example.kappacultivationmobile.battle.model.CellType
import com.example.kappacultivationmobile.models.Enemy
import kotlin.math.abs
import com.example.kappacultivationmobile.models.LevelMilestone

/**
 * BattleEngine 負責處理棋盤戰鬥的所有核心邏輯。
 * @param cols 棋盤寬度（列數）。
 * @param rows 棋盤高度（行數）。
 */
object BattleEngine {

    private var boardData: MutableList<BattleCell> = mutableListOf()
    private var cols = 0
    private var rows = 0

    // 儲存當前玩家和敵人的位置
    private lateinit var playerCell: BattleCell
    private val activeEnemies: MutableList<BattleCell> = mutableListOf()

    // 初始化棋盤數據
    fun initialize(cols: Int, rows: Int, initialEnemies: List<Enemy>) {
        this.cols = cols
        this.rows = rows

        // 創建空白棋盤
        boardData = MutableList(cols * rows) { index ->
            BattleCell(
                row = index / cols,
                col = index % cols,
                type = CellType.EMPTY
            )
        }

        placePlayerUnit()
        placeEnemies(initialEnemies)
    }

    fun getBoardData(): List<BattleCell> = boardData

    // 放置玩家單位 (固定在底部中央)
    private fun placePlayerUnit() {
        val playerCol = cols / 2
        val playerRow = rows - 1 // 最後一行
        val position = playerRow * cols + playerCol

        playerCell = boardData[position].apply {
            type = CellType.PLAYER
        }
    }

    // 隨機放置敵人 (棋盤上半部)
    private fun placeEnemies(enemies: List<Enemy>) {
        activeEnemies.clear()
        val numEnemies = (1..3).random().coerceAtMost(enemies.size)
        val availableIndices = (0 until (rows / 2) * cols).toMutableList() // 僅限上半部

        for (i in 0 until numEnemies) {
            if (availableIndices.isEmpty()) break

            val randomIndex = availableIndices.random()
            availableIndices.remove(randomIndex)

            val enemy = enemies[i] // 假設 enemies 列表有足夠的敵人
            val cell = boardData[randomIndex].apply {
                type = CellType.ENEMY
                this.enemy = enemy
            }
            activeEnemies.add(cell)
        }
    }

    // 將座標轉換為列表索引
    private fun getPosition(row: Int, col: Int): Int = row * cols + col

    // MARK: - 行為邏輯

    /** 根據曼哈頓距離計算玩家移動範圍 (例如：可移動 3 格) */
    fun getMovementRange(moveRange: Int): List<Int> {
        val range = mutableListOf<Int>()
        val startR = playerCell.row
        val startC = playerCell.col

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                // 使用曼哈頓距離：|r1 - r2| + |c1 - c2|
                if (abs(r - startR) + abs(c - startC) <= moveRange) {
                    val position = getPosition(r, c)
                    val cell = boardData[position]
                    // 確保目標格子是空的且不是玩家自己
                    if (cell.type == CellType.EMPTY) {
                        range.add(position)
                    }
                }
            }
        }
        return range
    }

    /** 執行玩家移動 */
    fun movePlayer(newPosition: Int): List<Int> {
        val oldPosition = getPosition(playerCell.row, playerCell.col)
        val newCell = boardData[newPosition]

        // 1. 更新舊位置為 EMPTY
        playerCell.type = CellType.EMPTY
        playerCell.playerUnitId = null

        // 2. 更新新位置為 PLAYER
        newCell.type = CellType.PLAYER
        newCell.playerUnitId = 1
        playerCell = newCell // 更新玩家單位的引用

        return listOf(oldPosition, newPosition)
    }

    data class EnemyTurnResult(
        val playerAttacked: Boolean,
        val movedPositions: List<Int>,
        val log: String,
        val totalDamage: Int // 這裡是關鍵
    )

    /** 執行敵人回合 (AI 動作) */
    fun performEnemyTurn(playerInfo: LevelMilestone): EnemyTurnResult {
        val movedPositions = mutableListOf<Int>()
        var totalDamage = 0 // ✅ 加總所有敵人的傷害
        var playerAttacked = false
        val log = StringBuilder()

        val enemiesToProcess = activeEnemies.toList()

        for (enemyCell in enemiesToProcess) {
            val enemy = enemyCell.enemy ?: continue
            val enemyPos = getPosition(enemyCell.row, enemyCell.col)

            if (isAdjacent(enemyCell.row, enemyCell.col, playerCell.row, playerCell.col)) {
                val damage = calculateDamage(enemy.attack, playerInfo.defense ?: 0)
                totalDamage += damage // ✅ 累加
                log.append("${enemy.name} 攻擊了你，造成 ${damage} 傷害！\n")
                playerAttacked = true
            } else {
                val (nextR, nextC) = calculateMoveTowards(enemyCell.row, enemyCell.col, playerCell.row, playerCell.col)
                val newPos = getPosition(nextR, nextC)

                if (boardData[newPos].type == CellType.EMPTY) {
                    val targetCell = boardData[newPos]
                    targetCell.type = CellType.ENEMY
                    targetCell.enemy = enemy
                    enemyCell.type = CellType.EMPTY
                    enemyCell.enemy = null

                    activeEnemies.remove(enemyCell)
                    activeEnemies.add(targetCell)

                    movedPositions.add(enemyPos)
                    movedPositions.add(newPos)

                    if (isAdjacent(nextR, nextC, playerCell.row, playerCell.col)) {
                        val damage = calculateDamage(enemy.attack, playerInfo.defense ?: 0)
                        totalDamage += damage // ✅ 移動後的攻擊也要累加
                        log.append("${enemy.name} 移動後攻擊了你，造成 ${damage} 傷害！\n")
                        playerAttacked = true
                    }
                }
            }
        }
        // ✅ 回傳包含 totalDamage 的結果物件
        return EnemyTurnResult(playerAttacked, movedPositions, log.toString(), totalDamage)
    }

    // 曼哈頓距離判斷是否相鄰
    private fun isAdjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        return abs(r1 - r2) + abs(c1 - c2) == 1
    }

    // 簡化的移動路徑：朝著玩家的行或列移動一格
    private fun calculateMoveTowards(r: Int, c: Int, targetR: Int, targetC: Int): Pair<Int, Int> {
        var newR = r
        var newC = c

        if (r != targetR) {
            newR += if (targetR > r) 1 else -1
        } else if (c != targetC) {
            newC += if (targetC > c) 1 else -1
        }
        return Pair(newR, newC)
    }

    // 重新使用舊的傷害計算邏輯
    fun calculateDamage(attackerAtk: Int, defenderDef: Int): Int {
        val base = attackerAtk - defenderDef
        return if (base < 1) 1 else base + (0..3).random()
    }

    // 獲取當前活躍在棋盤上的敵人數量
    fun getActiveEnemiesCount(): Int {
        // activeEnemies 是 BattleEngine 內部的 MutableList<BattleCell> 屬性
        return activeEnemies.size
    }
}