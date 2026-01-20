package com.example.kappacultivationmobile.battle

import com.example.kappacultivationmobile.battle.model.BattleCell
import com.example.kappacultivationmobile.battle.model.CellType
import com.example.kappacultivationmobile.models.Enemy
import com.example.kappacultivationmobile.models.Skill
import kotlin.math.abs
import com.example.kappacultivationmobile.models.LevelMilestone
import com.example.kappacultivationmobile.BattleDamageLogic

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

    // 定義技能攻擊的結果
    data class SkillResult(
        val success: Boolean,      // 是否施放成功 (MP 足夠)
        val damage: Int = 0,       // 造成的傷害
        val isKill: Boolean = false, // 是否擊殺
        val enemyName: String = "",
        val remainingMp: Int = 0,   // 剩餘 MP
        val message: String = ""    // 錯誤或成功訊息
    )

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

    // ✅ 新增：玩家攻擊邏輯 (回傳傷害值與是否擊殺)
    data class PlayerAttackResult(val damage: Int, val isKill: Boolean, val enemyName: String)

    fun performPlayerAttack(targetPosition: Int, playerStats: LevelMilestone): PlayerAttackResult? {
        val cell = boardData[targetPosition]
        val enemy = cell.enemy ?: return null

        // 1. 使用 BattleLogic 計算基礎傷害 (集中管理公式)
        val rawDamage = BattleDamageLogic.calculateDamage(playerStats.attack, enemy.defense)

        // 2. 處理技能倍率 (這裡先簡化，未來可擴充)
        val activeSkill = playerStats.skills.firstOrNull()
        val skillMultiplier = activeSkill?.multiplier ?: 1.0

        // 3. 處理防禦減免公式
        val damageMultiplier = 100.0 / (100.0 + enemy.defense)
        val finalDamage = (rawDamage * skillMultiplier * damageMultiplier).toInt().coerceAtLeast(1)

        // 4. 扣血
        val currentHp = enemy.currentHp ?: enemy.health
        enemy.currentHp = (currentHp - finalDamage).coerceAtLeast(0)

        var isKill = false
        if (enemy.currentHp == 0) {
            isKill = true
            activeEnemies.removeAll { it == cell }

            // 清空格子
            cell.type = CellType.EMPTY
            cell.enemy = null
        }

        return PlayerAttackResult(finalDamage, isKill, enemy.name)
    }

    /**
     * 執行玩家技能
     * @param targetPosition 目標格子索引
     * @param playerStats 玩家能力值
     * @param currentMp 玩家當前 MP
     * @param skill 要使用的技能
     */
    fun performPlayerSkill(
        targetPosition: Int,
        playerStats: LevelMilestone,
        currentMp: Int,
        skill: Skill
    ): SkillResult {

        val cell = boardData[targetPosition]
        val enemy = cell.enemy

        // 1. 防呆檢查：格子沒敵人
        if (enemy == null) {
            return SkillResult(false, message = "無效的目標！")
        }

        // 2. 檢查 MP 是否足夠
        if (currentMp < skill.mpCost) {
            return SkillResult(false, message = "魔力不足！需要 ${skill.mpCost} MP")
        }

        // 3. 計算傷害 (呼叫 Logic)
        val damage = BattleDamageLogic.calculateSkillDamage(playerStats.attack, enemy.defense, skill.multiplier)

        // 4. 扣除敵人血量
        val enemyHp = enemy.currentHp ?: enemy.health
        enemy.currentHp = (enemyHp - damage).coerceAtLeast(0)

        // 5. 扣除玩家 MP
        val newMp = currentMp - skill.mpCost

        // 6. 處理擊殺邏輯
        var isKill = false
        if (enemy.currentHp == 0) {
            isKill = true
            activeEnemies.removeAll { it == cell }
            cell.type = CellType.EMPTY
            cell.enemy = null
        }

        return SkillResult(
            success = true,
            damage = damage,
            isKill = isKill,
            enemyName = enemy.name,
            remainingMp = newMp,
            message = "使用了 ${skill.name}！"
        )
    }

    /** 執行敵人回合 (AI 動作) */
    fun performEnemyTurn(playerInfo: LevelMilestone): EnemyTurnResult {
        val movedPositions = mutableListOf<Int>()
        var totalDamage = 0 // ✅ 加總所有敵人的傷害
        var playerAttacked = false
        val log = StringBuilder()

        val enemiesToProcess = activeEnemies.toList()

        for (enemyCell in enemiesToProcess) {
            val enemy = enemyCell.enemy ?: continue
            var currentEnemyRow = enemyCell.row
            var currentEnemyCol = enemyCell.col

            // 1. 判斷攻擊：如果一開始就在旁邊，直接攻擊 (不移動)
            if (isAdjacent(currentEnemyRow, currentEnemyCol, playerCell.row, playerCell.col)) {
                val damage = BattleDamageLogic.calculateDamage(enemy.attack, playerInfo.defense ?: 0)
                totalDamage += damage // ✅ 累加
                log.append("${enemy.name} 攻擊了你，造成 ${damage} 傷害！\n")
                playerAttacked = true
            } else {
                // 2. 移動邏輯：隨機決定走 1 或 2 步
                val stepsToMove = (1..2).random() // ✅ 隨機 1~2 步
                var stepsTaken = 0
                // 使用迴圈來執行移動
                while (stepsTaken < stepsToMove) {
                    // 重新計算當前座標 (因為可能剛走了一步)
                    val enemyPos = getPosition(currentEnemyRow, currentEnemyCol)

                    // 計算下一步方向
                    val (nextR, nextC) = calculateMoveTowards(currentEnemyRow, currentEnemyCol, playerCell.row, playerCell.col)
                    val newPos = getPosition(nextR, nextC)

                    // 檢查目標格是否為空 (避免穿牆或重疊)
                    if (boardData[newPos].type == CellType.EMPTY) {
                        // 執行移動更新
                        val targetCell = boardData[newPos]
                        targetCell.type = CellType.ENEMY
                        targetCell.enemy = enemy

                        // 清除舊格子
                        val oldCell = boardData[enemyPos]
                        oldCell.type = CellType.EMPTY
                        oldCell.enemy = null

                        // 更新引用與座標
                        activeEnemies.remove(oldCell)
                        activeEnemies.add(targetCell)

                        currentEnemyRow = nextR
                        currentEnemyCol = nextC

                        movedPositions.add(enemyPos) // 紀錄軌跡
                        movedPositions.add(newPos)

                        stepsTaken++

                        // 如果走了一步後發現已經貼身，就停止移動準備攻擊
                        if (isAdjacent(currentEnemyRow, currentEnemyCol, playerCell.row, playerCell.col)) {
                            break
                        }
                    } else {
                        // 前方有障礙物，停止移動
                        break
                    }
                }

                // 3. 移動後的攻擊判定
                if (isAdjacent(currentEnemyRow, currentEnemyCol, playerCell.row, playerCell.col)) {
                    val damage = BattleDamageLogic.calculateDamage(enemy.attack, playerInfo.defense ?: 0)
                    totalDamage += damage
                    log.append("${enemy.name} 移動後攻擊了你，造成 ${damage} 傷害！\n")
                    playerAttacked = true
                }
            }
        }
        // 回傳包含 totalDamage 的結果物件
        return EnemyTurnResult(playerAttacked, movedPositions, log.toString(), totalDamage)
    }

    // 曼哈頓距離判斷是否相鄰
    private fun isAdjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        return abs(r1 - r2) + abs(c1 - c2) == 1
    }

    // 敵人總是選擇最短路徑走向玩家，優先「垂直移動」
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

    // 獲取當前活躍在棋盤上的敵人數量
    fun getActiveEnemiesCount(): Int {
        // activeEnemies 是 BattleEngine 內部的 MutableList<BattleCell> 屬性
        return activeEnemies.size
    }
}