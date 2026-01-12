package com.example.kappacultivationmobile.battle

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kappacultivationmobile.BgmManager
import com.example.kappacultivationmobile.LevelMilestone
import com.example.kappacultivationmobile.R
import com.example.kappacultivationmobile.battle.model.BattleCell
import com.example.kappacultivationmobile.battle.model.CellType
import com.example.kappacultivationmobile.battle.ui.BattleGridAdapter
import com.example.kappacultivationmobile.model.Enemy
import com.example.kappacultivationmobile.LevelCalculator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 戰鬥狀態機
enum class BattleState {
    PREPARING,          // 戰鬥準備中/初始化
    PLAYER_TURN_START,  // 玩家回合開始 (選擇行動)
    PLAYER_MOVING,      // 玩家選擇移動目的地
    PLAYER_ATTACKING,   // 玩家選擇攻擊目標
    ENEMY_TURN,         // 敵人回合
    BATTLE_END          // 戰鬥結束 (勝利/失敗)
}

class BattleActivity : AppCompatActivity() {

    // 棋盤尺寸定義
    private val CELL_WIDTH_DP = 50f
    private val BOARD_ROWS = 14

    // UI 元件
    private lateinit var chessboard: RecyclerView
    private lateinit var battleLogTextView: TextView
    private lateinit var playerHpTextView: TextView
    private lateinit var playerHpBar: ProgressBar
    private lateinit var actionMenuLayout: ConstraintLayout
    private lateinit var moveButton: ImageButton
    private lateinit var attackButton: ImageButton
    private lateinit var endTurnButton: ImageButton
    private lateinit var escapeButton: ImageButton
    private lateinit var enemyStatusSummary: TextView

    // 戰鬥核心數據
    private lateinit var adapter: BattleGridAdapter
    private lateinit var initialEnemies: List<Enemy>

    private var currentPlayerHp = 0
    private var currentPlayerMp: Int = 0
    private lateinit var levelMilestones: List<LevelMilestone>
    private lateinit var currentPlayerStats: LevelMilestone

    private var currentCols = 0 // 實際計算出的列數
    private var currentState = BattleState.PREPARING
    private var playerMoveRange = 3 // 玩家可移動的格子數

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle)

        // 1. 數據初始化
        loadGameData()

        // 2. UI 綁定
        bindUI()

        // 3. 初始化棋盤並設置佈局
        initChessboard()

        // 4. 設置按鈕監聽器
        setupListeners()
    }

    private fun loadGameData() {
        // 從 Intent 獲取敵人數據
        val singleEnemy = intent.getSerializableExtra("enemy") as? Enemy
        if (singleEnemy == null) {
            Toast.makeText(this, "無法載入戰鬥數據", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // 假設我們要和 3 個相同的敵人戰鬥
        initialEnemies = listOf(singleEnemy.copy(), singleEnemy.copy(), singleEnemy.copy())

        val sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE)
        val playerLevel = sharedPreferences.getInt("currentLevel", 1)

        // 載入 LevelInfo
        // 1. 呼叫我們剛改好的 loadLevelInfo，它現在會回傳 LevelMilestone 並初始化計算機
        val milestone = loadLevelInfo(playerLevel)
        currentPlayerStats = milestone // 確保你在 Activity 頂部有宣告這個變數

        // 2. 更新血量邏輯
        // 注意：現在是用 currentPlayerStats.health 而非 playerInfo.health
        currentPlayerHp = sharedPreferences.getInt("currentHp", currentPlayerStats.health)

        // 3. (選用) 如果你有 MP 需求，也可以在這裡初始化
        currentPlayerMp = sharedPreferences.getInt("currentMp", currentPlayerStats.mana)

        // 播放 BGM
        BgmManager.play(this, R.raw.battle_bgm, "Battle")
    }

    private fun bindUI() {
        chessboard = findViewById(R.id.chessboard)
        battleLogTextView = findViewById(R.id.battleLogTextView)
        playerHpTextView = findViewById(R.id.playerHpTextView)
        playerHpBar = findViewById(R.id.playerHpBar)
        actionMenuLayout = findViewById(R.id.actionMenuLayout)
        moveButton = findViewById(R.id.moveButton)
        attackButton = findViewById(R.id.attackButton)
        endTurnButton = findViewById(R.id.endTurnButton)
        escapeButton = findViewById(R.id.escapeButton)
        enemyStatusSummary = findViewById(R.id.enemyStatusSummary)

        updateHealthUI()
    }

    // 棋盤列數並初始化 Engine
    private fun initChessboard() {
        chessboard.post {
            // ✅ 針對 Pixel 9 Pro 類型的螢幕，固定設定為 8 列 (或您喜歡的數量)
            // 這樣就不會因為 CELL_WIDTH_DP 的微小誤差導致超出螢幕
            currentCols = 8

            // 強制設定 GridLayoutManager
            chessboard.layoutManager = GridLayoutManager(this, currentCols)

            // 初始化引擎
            BattleEngine.initialize(currentCols, BOARD_ROWS, initialEnemies)

            val boardData = BattleEngine.getBoardData()
            adapter = BattleGridAdapter(boardData.toMutableList()) { position -> onTileClicked(position) }
            chessboard.adapter = adapter

            currentState = BattleState.PLAYER_TURN_START
            updateUIForTurn()
        }
    }

    private fun setupListeners() {
        moveButton.setOnClickListener {
            actionMenuLayout.visibility = View.GONE
            if (currentState == BattleState.PLAYER_TURN_START) {
                currentState = BattleState.PLAYER_MOVING
                updateBattleLog("請點擊格子選擇移動目的地 (範圍 ${playerMoveRange} 格)。")
                showMovementRange()
                updateUIForTurn()
            }
        }

        attackButton.setOnClickListener {
            actionMenuLayout.visibility = View.GONE
            if (currentState == BattleState.PLAYER_TURN_START) {
                currentState = BattleState.PLAYER_ATTACKING
                updateBattleLog("請點擊格子選擇攻擊目標 (範圍 1 格)。")
                // TODO: 顯示攻擊範圍
                updateUIForTurn()
            }
        }

        endTurnButton.setOnClickListener {
            if (currentState == BattleState.PLAYER_TURN_START) {
                // 結束玩家回合，進入敵人回合
                actionMenuLayout.visibility = View.GONE
                clearHighlights()
                startEnemyTurn()
            }
        }

        escapeButton.setOnClickListener {
            actionMenuLayout.visibility = View.GONE
            handleEscape()
        }
    }

    // 點擊棋盤格子的處理函數
    private fun onTileClicked(position: Int) {
        val cell = adapter.boardData[position]

        // ✅ 如果玩家正在選擇目標，但又點擊了「自己」所在的格子，則視為取消並重開選單
        if (cell.type == CellType.PLAYER &&
            (currentState == BattleState.PLAYER_ATTACKING || currentState == BattleState.PLAYER_MOVING)) {

            currentState = BattleState.PLAYER_TURN_START
            showActionMenuAtTile(position) // 重新叫出選單
            updateBattleLog("已取消動作，請重新選擇")
            return
        }

        when (currentState) {
            BattleState.PLAYER_TURN_START -> {
                if (cell.type == CellType.PLAYER) {
                    // ✅ 點選角色圖片時出現選單
                    showActionMenuAtTile(position)
                } else {
                    actionMenuLayout.visibility = View.GONE
                }
            }
            BattleState.PLAYER_MOVING -> {
                actionMenuLayout.visibility = View.GONE // 開始移動後隱藏選單
                if (cell.type == CellType.HIGHLIGHT_MOVE) {
                    handleMoveAction(position)
                }
            }
            BattleState.PLAYER_ATTACKING -> {
                // TODO: 實現攻擊選擇邏輯
                if (cell.type == CellType.ENEMY) {
                    handleAttackAction(position)
                } else {
                    updateBattleLog("請選擇一個敵人進行攻擊。")
                }
            }
            else -> {
                updateBattleLog("現在不是你行動的時間。")
            }
        }
    }

    private fun showActionMenuAtTile(position: Int) {
        val viewHolder = chessboard.findViewHolderForAdapterPosition(position) ?: return
        val itemView = viewHolder.itemView

        val coords = IntArray(2)
        itemView.getLocationInWindow(coords)

        // 取得 RecyclerView 在視窗中的偏移
        val parentCoords = IntArray(2)
        findViewById<View>(R.id.battleBackground).getLocationInWindow(parentCoords)

        // 計算相對於父容器的座標
        val relativeX = coords[0] - parentCoords[0]
        val relativeY = coords[1] - parentCoords[1]

        // 設定選單中心點
        actionMenuLayout.translationX = relativeX.toFloat() - (actionMenuLayout.width / 2) + (itemView.width / 2)
        actionMenuLayout.translationY = relativeY.toFloat() - (actionMenuLayout.height / 2) + (itemView.height / 2)

        actionMenuLayout.visibility = View.VISIBLE
    }

    // 處理移動動作
    private fun handleMoveAction(newPosition: Int) {
        actionMenuLayout.visibility = View.GONE // ✅ 移動後隱藏選單
        val updatedPositions = BattleEngine.movePlayer(newPosition)

        // 更新 UI
        adapter.updateMultipleTiles(updatedPositions)
        updateBattleLog("玩家移動至新位置。")

        // 清理高亮並切換到敵人回合
        clearHighlights()
        startEnemyTurn()
    }

    // 處理攻擊動作
    private fun handleAttackAction(enemyPosition: Int) {
        actionMenuLayout.visibility = View.GONE
        val cell = adapter.boardData[enemyPosition]
        val enemy = cell.enemy ?: return

        // 1. 更新敵人血量 (假設 Enemy 有 currentHp 變數)
        val currentAtk = currentPlayerStats.attack

        // B. 取得目前要使用的技能 (暫時先預設使用第一個技能，後續可以做技能選單)
        val activeSkill = currentPlayerStats.skills[0]

        // C. 基礎傷害 = 玩家當前攻擊力 * 技能倍率
        val baseDamage = currentAtk * activeSkill.multiplier

        // D. 比例減傷公式：100 / (100 + 敵人防禦)
        val damageMultiplier = 100.0 / (100.0 + enemy.defense)

        // E. 最終傷害 (至少造成 1 點傷害)
        val damage = (baseDamage * damageMultiplier).toInt().coerceAtLeast(1)

        val currentHp = enemy.currentHp ?: enemy.health
        enemy.currentHp = (currentHp - damage).coerceAtLeast(0)

        // 2. 刷新該格子顯示血條
        adapter.notifyItemChanged(enemyPosition)
        updateBattleLog("你攻擊 ${enemy.name} 造成 $damage 傷害！")

        // 3. 檢查敵人是否死亡
        if (enemy.currentHp == 0) {
            cell.type = CellType.EMPTY
            cell.enemy = null
            // 從 Engine 的 activeEnemies 移除 (建議在 Engine 加一個 removeEnemy 函式)
            updateBattleLog("${enemy.name} 被擊敗了！")
        }
        startEnemyTurn()
    }

    // 顯示可移動範圍
    private fun showMovementRange() {
        val movePositions = BattleEngine.getMovementRange(playerMoveRange)
        val highlightedPositions = mutableListOf<Int>()

        movePositions.forEach { pos ->
            adapter.boardData[pos].type = CellType.HIGHLIGHT_MOVE
            highlightedPositions.add(pos)
        }
        adapter.updateMultipleTiles(highlightedPositions)
    }

    // 清除所有高亮
    private fun clearHighlights() {
        val positionsToUpdate = mutableListOf<Int>()
        adapter.boardData.forEachIndexed { index, cell ->
            if (cell.type == CellType.HIGHLIGHT_MOVE || cell.type == CellType.HIGHLIGHT_ATTACK) {
                // 必須將高亮狀態的格子恢復為它們原本的類型 (例如 EMPTY)
                cell.type = CellType.EMPTY
                positionsToUpdate.add(index)
            }
        }
        adapter.updateMultipleTiles(positionsToUpdate)
    }

    // 敵人回合開始
    private fun startEnemyTurn() {
        currentState = BattleState.ENEMY_TURN
        updateUIForTurn()
        updateBattleLog("敵人回合開始...")

        Handler(Looper.getMainLooper()).postDelayed({
            // ✅ 接收包含 totalDamage 的 result
            val result = BattleEngine.performEnemyTurn(currentPlayerStats)

            adapter.updateMultipleTiles(result.movedPositions)
            updateBattleLog(result.log)

            if (result.playerAttacked) {
                // ✅ 修正：使用實際計算出的傷害，不再使用固定值 5
                currentPlayerHp -= result.totalDamage

                if (currentPlayerHp < 0) currentPlayerHp = 0
                updateHealthUI()
            }

            if (currentPlayerHp == 0) {
                handleBattleEnd(false)
            } else {
                currentState = BattleState.PLAYER_TURN_START
                updateBattleLog("你的回合！")
                updateUIForTurn()
            }
        }, 1500)
    }

    // 根據狀態更新按鈕啟用狀態
    private fun updateUIForTurn() {
        val isPlayerTurn = currentState == BattleState.PLAYER_TURN_START

        moveButton.isEnabled = isPlayerTurn
        attackButton.isEnabled = isPlayerTurn
        endTurnButton.isEnabled = isPlayerTurn

        if (currentState == BattleState.ENEMY_TURN || currentState == BattleState.BATTLE_END) {
            moveButton.isEnabled = false
            attackButton.isEnabled = false
            endTurnButton.isEnabled = false
        }

        // TODO: 根據 BattleEngine 中的敵人列表更新 enemyStatusSummary
        enemyStatusSummary.text = "敵人數量：${BattleEngine.getActiveEnemiesCount()}"
    }

    private fun updateHealthUI() {
        // 使用插值計算後的精確最大血量
        playerHpTextView.text = "玩家 HP: $currentPlayerHp / ${currentPlayerStats.health}"

        // 同步更新血條的最大值
        playerHpBar.max = currentPlayerStats.health
        playerHpBar.progress = currentPlayerHp
    }

    private fun updateBattleLog(message: String) {
        // 只保留最近幾條記錄
        val maxLines = 5
        val lines = battleLogTextView.text.toString().split("\n").toMutableList()
        lines.add(message)

        while (lines.size > maxLines) {
            lines.removeAt(0) // 移除最舊的紀錄
        }
        battleLogTextView.text = lines.joinToString("\n")
    }

    // 戰鬥結束處理
    private fun handleBattleEnd(win: Boolean) {
        currentState = BattleState.BATTLE_END

        // 儲存最終 HP 到 SharedPreferences
        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
            .putInt("currentHp", currentPlayerHp)
            .apply()

        val message = if (win) "戰鬥勝利！獲得經驗與獎勵。" else "你戰敗了...HP歸零。"
        updateBattleLog(message)

        // 禁用所有按鈕
        updateUIForTurn()

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3000)
    }

    private fun handleEscape() {
        val success = Math.random() < 0.5 // 50% 機率
        if (success) {
            updateBattleLog("你成功逃跑了。")
            getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                .putInt("currentHp", currentPlayerHp)
                .apply()
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1000)
        } else {
            updateBattleLog("逃跑失敗！敵人反擊！")
            startEnemyTurn() // 敵人先打一輪
        }
    }

    // 載入 LevelInfo 邏輯 (需要與您的 LevelInfo 類別匹配)
    private fun loadLevelInfo(level: Int): LevelMilestone {
        val json = assets.open("level_info.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<LevelMilestone>>() {}.type
        val milestones: List<LevelMilestone> = Gson().fromJson(json, type)

        // 使用你的計算機初始化並取得精確數值
        LevelCalculator.init(milestones)
        currentPlayerStats = LevelCalculator.getStatsForLevel(level)

        return currentPlayerStats
    }

    override fun onResume() {
        super.onResume()
        BgmManager.resume()
    }

    override fun onPause() {
        super.onPause()
        BgmManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        BgmManager.stop()
    }
}