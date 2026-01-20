package com.example.kappacultivationmobile.battle

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
import com.example.kappacultivationmobile.models.LevelMilestone
import com.example.kappacultivationmobile.R
import com.example.kappacultivationmobile.battle.model.CellType
import com.example.kappacultivationmobile.battle.ui.BattleGridAdapter
import com.example.kappacultivationmobile.models.Enemy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.kappacultivationmobile.LevelManager

// 戰鬥狀態機
enum class BattleState {
    PREPARING,          // 戰鬥準備中/初始化
    PLAYER_TURN_START,  // 玩家回合開始 (選擇行動)
    PLAYER_MOVING,      // 玩家選擇移動目的地
    PLAYER_ATTACKING,   // 玩家選擇攻擊目標
    PLAYER_SKILL_SELECTION,   // 玩家技能
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
    private lateinit var playerMpTextView: TextView
    private lateinit var playerMpBar: ProgressBar
    private lateinit var actionMenuLayout: View
    private lateinit var moveButton: ImageButton
    private lateinit var attackButton: ImageButton
    private lateinit var skillButton: ImageButton
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

    private val levelManager = LevelManager()

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

        // ✅ 修改處：使用 LevelManager 取得玩家數值
        currentPlayerStats = levelManager.getStatsForLevel(playerLevel)

        // 2. 更新HP邏輯
        // 注意：現在是用 currentPlayerStats.health 而非 playerInfo.health
        currentPlayerHp = sharedPreferences.getInt("currentHp", currentPlayerStats.health)

        // 3. 更新MP邏輯
        currentPlayerMp = sharedPreferences.getInt("currentMp", currentPlayerStats.mana)

        // 播放 BGM
        BgmManager.play(this, R.raw.battle_bgm, "Battle")
    }

    private fun bindUI() {
        chessboard = findViewById(R.id.chessboard)
        battleLogTextView = findViewById(R.id.battleLogTextView)
        playerHpTextView = findViewById(R.id.playerHpTextView)
        playerHpBar = findViewById(R.id.playerHpBar)
        playerMpTextView = findViewById(R.id.playerMpTextView)
        playerMpBar = findViewById(R.id.playerMpBar)
        actionMenuLayout = findViewById(R.id.actionMenuLayout)
        moveButton = findViewById(R.id.moveButton)
        attackButton = findViewById(R.id.attackButton)
        skillButton = findViewById(R.id.skillButton)
        endTurnButton = findViewById(R.id.endTurnButton)
        escapeButton = findViewById(R.id.escapeButton)
        enemyStatusSummary = findViewById(R.id.enemyStatusSummary)

        updateStatusUI()
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

        // 技能按鈕點擊
        skillButton.setOnClickListener {
            actionMenuLayout.visibility = View.GONE
            if (currentState == BattleState.PLAYER_TURN_START) {

                // 檢查是否有技能 (防呆)
                if (currentPlayerStats.skills.isEmpty()) {
                    updateBattleLog("你還沒有學會任何技能！")
                    // 重新顯示選單
                    actionMenuLayout.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                currentState = BattleState.PLAYER_SKILL_SELECTION
                updateBattleLog("請選擇技能施放目標。")

                // TODO: 如果技能有特定範圍，這裡應該呼叫 showSkillRange()
                // 目前先假設範圍跟攻擊一樣是 1 格
                // updateUIForTurn()
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
            (currentState == BattleState.PLAYER_ATTACKING ||
                    currentState == BattleState.PLAYER_MOVING ||
                    currentState == BattleState.PLAYER_SKILL_SELECTION)) {
            currentState = BattleState.PLAYER_TURN_START
            showActionMenuAtTile(position)
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
            BattleState.PLAYER_SKILL_SELECTION -> {
                if (cell.type == CellType.ENEMY) {
                    // 這裡呼叫釋放技能的邏輯
                    handleSkillAction(position)
                } else {
                    updateBattleLog("請選擇一個敵人作為技能目標。")
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

        // ✅ 呼叫 Engine 處理邏輯，Activity 只負責顯示結果
        val result = BattleEngine.performPlayerAttack(enemyPosition, currentPlayerStats)

        if (result != null) {
            // 1. 刷新格子顯示 (血條變化/敵人消失)
            adapter.notifyItemChanged(enemyPosition)

            // 2. 顯示 Log
            updateBattleLog("你攻擊 ${result.enemyName} 造成 ${result.damage} 傷害！")

            if (result.isKill) {
                updateBattleLog("${result.enemyName} 被擊敗了！")
                // TODO: 播放死亡音效或特效
            }

            // 3. 進入敵人回合
            startEnemyTurn()
        } else {
            // 防呆：如果點擊時敵人已經不見了
            currentState = BattleState.PLAYER_TURN_START
        }
    }

    private fun handleSkillAction(enemyPosition: Int) {
        actionMenuLayout.visibility = View.GONE

        // 1. 取得當前要使用的技能
        // 目前先預設使用列表中的第一個技能
        // 未來您可以做一個彈窗讓玩家選技能
        val skillToUse = currentPlayerStats.skills.firstOrNull()

        if (skillToUse == null) {
            updateBattleLog("你沒有可用的技能！")
            currentState = BattleState.PLAYER_TURN_START
            return
        }

        // 2. 呼叫 Engine 執行技能
        val result = BattleEngine.performPlayerSkill(
            targetPosition = enemyPosition,
            playerStats = currentPlayerStats,
            currentMp = currentPlayerMp, // 需確保您有定義這個變數
            skill = skillToUse
        )

        if (!result.success) {
            // 失敗 (例如 MP 不足)
            updateBattleLog(result.message)
            // 回到待機狀態，不結束回合，讓玩家可以改用普攻
            currentState = BattleState.PLAYER_TURN_START
            return
        }

        // 3. 成功：更新 UI
        currentPlayerMp = result.remainingMp
        // TODO: 更新 MP 條 UI (例如 playerMpBar.progress = currentPlayerMp)

        adapter.notifyItemChanged(enemyPosition) // 刷新敵人狀態
        updateBattleLog("${result.message} 對 ${result.enemyName} 造成 ${result.damage} 傷害！")

        if (result.isKill) {
            updateBattleLog("${result.enemyName} 被消滅了！")
        }

        // 4. 技能耗費行動，回合結束
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
                updateStatusUI()
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

    private fun updateStatusUI() {
        // 1. 更新 HP
        playerHpTextView.text = "HP: $currentPlayerHp / ${currentPlayerStats.health}"
        playerHpBar.max = currentPlayerStats.health
        playerHpBar.progress = currentPlayerHp

        // 2. ✅ 更新 MP
        // 確保 currentPlayerMp 已經在 loadGameData 中初始化
        playerMpTextView.text = "MP: $currentPlayerMp / ${currentPlayerStats.mana}"
        playerMpBar.max = currentPlayerStats.mana
        playerMpBar.progress = currentPlayerMp
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