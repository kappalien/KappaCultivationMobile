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

    // 隨機放置玩家 (限制在棋盤下半部，避免與上半部的敵人重疊)
    private fun placePlayerUnit() {
        // 1. 定義玩家生成的區域範圍
        // 敵人在上半部 (0 ~ rows/2)，所以玩家範圍從 rows/2 開始直到棋盤結束
        val startRow = rows / 2
        val totalRows = rows

        val availableIndices = mutableListOf<Int>()

        // 2. 收集下半部所有的空位座標
        for (r in startRow until totalRows) {
            for (c in 0 until cols) {
                val pos = getPosition(r, c)
                // 檢查該格子是否為空 (雖然初始化時通常是空的，但這是好習慣)
                if (boardData[pos].type == CellType.EMPTY) {
                    availableIndices.add(pos)
                }
            }
        }

        // 3. 從可用位置中隨機挑選一個
        if (availableIndices.isNotEmpty()) {
            val randomPos = availableIndices.random()

            playerCell = boardData[randomPos].apply {
                type = CellType.PLAYER
            }
        } else {
            // 防呆機制：如果真的沒位子 (極端情況)，就放回原本的底部中央
            val fallbackPos = getPosition(rows - 1, cols / 2)
            playerCell = boardData[fallbackPos].apply {
                type = CellType.PLAYER
            }
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

        // 1. 普攻
        val basicAttackMultiplier = 1.0

        // 直接呼叫 Logic，傳入 1.0
        val finalDamage = BattleDamageLogic.calculateFinalDamage(
            atk = playerStats.attack,
            def = enemy.defense,
            skillMultiplier = basicAttackMultiplier
        )

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

    /**
     * 智慧尋路：嘗試靠近目標。
     * 如果最佳路徑被擋住，會嘗試繞路。
     */
    private fun getSmartMoveTowards(currentR: Int, currentC: Int, targetR: Int, targetC: Int): Pair<Int, Int> {
        val distR = targetR - currentR
        val distC = targetC - currentC

        // 定義優先順序：距離較遠的軸向優先
        val moves = mutableListOf<Pair<Int, Int>>()
        if (kotlin.math.abs(distR) >= kotlin.math.abs(distC)) {
            if (distR != 0) moves.add(Pair(if (distR > 0) 1 else -1, 0)) // 垂直
            if (distC != 0) moves.add(Pair(0, if (distC > 0) 1 else -1)) // 水平
        } else {
            if (distC != 0) moves.add(Pair(0, if (distC > 0) 1 else -1)) // 水平
            if (distR != 0) moves.add(Pair(if (distR > 0) 1 else -1, 0)) // 垂直
        }

        // 嘗試所有可能的方向
        for ((dr, dc) in moves) {
            val checkR = currentR + dr
            val checkC = currentC + dc

            // 檢查邊界
            if (checkR in 0 until rows && checkC in 0 until cols) {
                val checkPos = getPosition(checkR, checkC)
                // 檢查是否為空格
                if (boardData[checkPos].type == CellType.EMPTY) {
                    return Pair(checkR, checkC) // 找到路徑
                }
            }
        }

        // 無路可走，回傳原點
        return Pair(currentR, currentC)
    }

    // 獲取當前活躍在棋盤上的敵人數量
    fun getActiveEnemiesCount(): Int {
        // activeEnemies 是 BattleEngine 內部的 MutableList<BattleCell> 屬性
        return activeEnemies.size
    }

    // ✅ 新增：取得當前活躍敵人的列表 (回傳複製的列表以避免並發修改問題)
    fun getEnemyList(): List<BattleCell> {
        return activeEnemies.toList()
    }

    // ✅ 修改：只處理「單一」敵人的回合邏輯
    // 回傳值改為單次行動的結果
    fun processSingleEnemyAction(enemyCell: BattleCell, playerStats: LevelMilestone): EnemyTurnResult {
        val enemy = enemyCell.enemy!!
        val movedPositions = mutableListOf<Int>()
        var damageDealt = 0
        var playerAttacked = false
        val logBuilder = StringBuilder()

        // 紀錄當前位置 (會隨著移動更新)
        var currentR = enemyCell.row
        var currentC = enemyCell.col

        // 1. 判斷是否直接攻擊 (不用移動)
        if (isAdjacent(currentR, currentC, playerCell.row, playerCell.col)) {
            val dmg = BattleDamageLogic.calculateFinalDamage(enemy.attack, playerStats.defense ?: 0)
            damageDealt = dmg
            playerAttacked = true
            logBuilder.append("${enemy.name} 攻擊了你，造成 $dmg 傷害！\n")
        } else {
            // 2. 移動邏輯
            val stepsToMove = (1..2).random()
            var stepsTaken = 0

            while (stepsTaken < stepsToMove) {
                val enemyPos = getPosition(currentR, currentC)

                // ✅ 使用智慧尋路
                val (nextR, nextC) = getSmartMoveTowards(currentR, currentC, playerCell.row, playerCell.col)
                val newPos = getPosition(nextR, nextC)

                // 如果尋路結果還是原點，代表無路可走，停止移動
                if (newPos == enemyPos) break

                // 執行移動
                if (boardData[newPos].type == CellType.EMPTY) {
                    val targetCell = boardData[newPos]
                    targetCell.type = CellType.ENEMY
                    targetCell.enemy = enemy

                    val oldCell = boardData[enemyPos]
                    oldCell.type = CellType.EMPTY
                    oldCell.enemy = null

                    activeEnemies.remove(oldCell)
                    activeEnemies.add(targetCell)

                    // 更新當前座標
                    currentR = nextR
                    currentC = nextC

                    movedPositions.add(enemyPos)
                    movedPositions.add(newPos)
                    stepsTaken++

                    // 如果移動後已經貼身，就停止移動準備攻擊
                    if (isAdjacent(currentR, currentC, playerCell.row, playerCell.col)) {
                        break
                    }
                } else {
                    break // 被擋住了
                }
            }

            // 3. 移動後的攻擊判定
            // ✅ 修正：這裡直接使用 updated 的 currentR, currentC，解決 Unresolved reference
            if (isAdjacent(currentR, currentC, playerCell.row, playerCell.col)) {
                val dmg = BattleDamageLogic.calculateFinalDamage(enemy.attack, playerStats.defense ?: 0)
                damageDealt += dmg
                playerAttacked = true
                logBuilder.append("${enemy.name} 移動後攻擊了你，造成 $dmg 傷害！\n")
            }
        }

        return EnemyTurnResult(playerAttacked, movedPositions, logBuilder.toString(), damageDealt)
    }

    // 清理已死亡的敵人 (同步 activeEnemies 清單)
    fun refreshActiveEnemies() {
        // 移除條件：格子變成了 EMPTY，或是敵人資料是 null，或是血量 <= 0
        activeEnemies.removeAll { cell ->
            cell.type == CellType.EMPTY || cell.enemy == null || (cell.enemy?.currentHp ?: 0) <= 0
        }
    }

    fun clear() {
        boardData.clear()
        activeEnemies.clear()
        cols = 0
        rows = 0
        // 如果有其他狀態變數 (如 currentPlayerStats)，也要在這裡重置
    }
}