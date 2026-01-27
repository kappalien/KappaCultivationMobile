package com.example.kappacultivationmobile.battle

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.kappacultivationmobile.models.LevelMilestone
import com.example.kappacultivationmobile.models.Skill
import com.example.kappacultivationmobile.models.Enemy
import com.example.kappacultivationmobile.models.AoeEffectType
import com.example.kappacultivationmobile.battle.model.CellType
import com.example.kappacultivationmobile.R
import com.example.kappacultivationmobile.BgmManager
import com.example.kappacultivationmobile.battle.ui.BattleGridAdapter
import com.example.kappacultivationmobile.LevelManager
import com.example.kappacultivationmobile.BattleDamageLogic

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
    private val BOARD_COLS = 8
    private val BOARD_ROWS = 12

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
    private lateinit var effectManager: BattleEffectManager
    private lateinit var initialEnemies: List<Enemy>

    private var currentPlayerHp = 0
    private var currentPlayerMp: Int = 0
    private var selectedSkill: Skill? = null
    private lateinit var levelMilestones: List<LevelMilestone>
    private lateinit var currentPlayerStats: LevelMilestone


    //private var currentCols = 0 // 實際計算出的列數
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

        // 初始化 EffectManager
        val effectContainer = findViewById<FrameLayout>(R.id.effectContainer)
        effectManager = BattleEffectManager(this, effectContainer)

        updateStatusUI()
    }

    // 棋盤列數並初始化 Engine
    private fun initChessboard() {
        chessboard.post {
            // ✅ 針對 Pixel 9 Pro 類型的螢幕，固定設定為 8 列 (或您喜歡的數量)
            // 這樣就不會因為 CELL_WIDTH_DP 的微小誤差導致超出螢幕
            //currentCols = 8

            // 強制設定 GridLayoutManager
            chessboard.layoutManager = GridLayoutManager(this, BOARD_COLS)

            // 初始化引擎
            BattleEngine.initialize(BOARD_COLS, BOARD_ROWS, initialEnemies)

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
                // 呼叫彈窗讓玩家選
                showSkillSelectionDialog()
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

        // 1. 先顯示選單，這樣才能在 post 裡面抓到正確的選單寬高
        actionMenuLayout.visibility = View.VISIBLE

        // 使用 post 確保 Layout 完成後再計算座標
        actionMenuLayout.post {
            // --- A. 準備數據 ---
            val menuWidth = actionMenuLayout.width
            val menuHeight = actionMenuLayout.height

            // 取得父容器 (ConstraintLayout) 的寬高，也就是螢幕可用區域
            val parentView = actionMenuLayout.parent as View
            val parentWidth = parentView.width
            val parentHeight = parentView.height

            // 設定一個安全邊距 (例如 16dp)，不要讓選單緊貼著螢幕邊緣
            val padding = (16 * resources.displayMetrics.density).toInt()

            // --- B. 取得格子的螢幕座標 ---
            val coords = IntArray(2)
            itemView.getLocationInWindow(coords)

            val parentCoords = IntArray(2)
            parentView.getLocationInWindow(parentCoords)

            // 計算格子相對於父容器的座標
            val relativeX = coords[0] - parentCoords[0]
            val relativeY = coords[1] - parentCoords[1]

            // --- C. 計算「理想」的置中位置 (您原本的邏輯) ---
            var targetX = relativeX + (itemView.width / 2) - (menuWidth / 2)
            var targetY = relativeY + (itemView.height / 2) - (menuHeight / 2)

            // --- D. 關鍵修正：邊界限制 (Clamping) ---
            // 如果 targetX < padding，強制設為 padding (防止超出左邊)
            // 如果 targetX > parentWidth - menuWidth，強制設為最大值 (防止超出右邊)

            // X 軸限制：左邊界 ~ 右邊界
            targetX = targetX.coerceIn(padding, parentWidth - menuWidth - padding)

            // Y 軸限制：上邊界 ~ 下邊界
            targetY = targetY.coerceIn(padding, parentHeight - menuHeight - padding)

            // --- E. 套用座標 ---
            actionMenuLayout.translationX = targetX.toFloat()
            actionMenuLayout.translationY = targetY.toFloat()
        }
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
            adapter.notifyItemChanged(enemyPosition)
            enemyStatusSummary.text = "敵人數量：${BattleEngine.getActiveEnemiesCount()}"
            updateBattleLog("你攻擊 ${result.enemyName} 造成 ${result.damage} 傷害！")

            if (result.isKill) {
                updateBattleLog("${result.enemyName} 被擊敗了！")

                // 檢查是否勝利
                if (BattleEngine.getActiveEnemiesCount() == 0) {
                    handleBattleEnd(true) // 觸發勝利結算
                    return // 結束函式，不進入敵人回合
                }
            }
            startEnemyTurn()
        } else {
            // 防呆：如果點擊時敵人已經不見了
            currentState = BattleState.PLAYER_TURN_START
        }
    }

    private fun handleSkillAction(enemyPosition: Int) {

        // 1. 取得剛剛選好的技能 (原本是 val skillToUse = currentPlayerStats.skills.firstOrNull())
        val skillToUse = selectedSkill

        // 防呆：理論上這時候 selectedSkill 不該是 null
        if (skillToUse == null) {
            updateBattleLog("技能選擇發生錯誤，請重新選擇。")
            currentState = BattleState.PLAYER_TURN_START
            return
        }

        // 2. 呼叫 Engine 執行技能
        val result = BattleEngine.performPlayerSkill(
            targetPosition = enemyPosition,
            playerStats = currentPlayerStats,
            currentMp = currentPlayerMp,
            skill = skillToUse
        )

        if (!result.success) {
            // 失敗 (例如 MP 不足，雖然前面檢查過但保留雙重保險)
            updateBattleLog(result.message)
            // 回到待機狀態
            currentState = BattleState.PLAYER_TURN_START
            selectedSkill = null // 重置選擇
            return
        }

        // 3. 成功：更新 UI
        currentPlayerMp = result.remainingMp

        // ✅ 更新 MP 條 (記得之前我們加過 updateStatusUI)
        updateStatusUI()

        adapter.notifyItemChanged(enemyPosition) // 刷新敵人狀態
        updateBattleLog("${result.message} 對 ${result.enemyName} 造成 ${result.damage} 傷害！")

        if (result.isKill) {
            updateBattleLog("${result.enemyName} 被消滅了！")

            // 檢查是否勝利
            if (BattleEngine.getActiveEnemiesCount() == 0) {
                handleBattleEnd(true)
                return
            }
        }

        // 4. 清理狀態與回合結束
        selectedSkill = null // ✅ 用完記得清空，避免下次誤用
        startEnemyTurn()
    }

    // 顯示Skll選單
    private fun showSkillSelectionDialog() {
        val skills = currentPlayerStats.skills

        if (skills.isEmpty()) {
            updateBattleLog("你還沒有學會任何技能！")
            actionMenuLayout.visibility = View.VISIBLE
            return
        }

        val skillNames = skills.map { "${it.name} (MP: ${it.mpCost})" }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("選擇要使用的技能")
            .setItems(skillNames) { _, which ->
                val chosenSkill = skills[which]

                // 檢查 MP
                if (currentPlayerMp < chosenSkill.mpCost) {
                    updateBattleLog("MP 不足，無法使用 ${chosenSkill.name}")
                    actionMenuLayout.visibility = View.VISIBLE
                    return@setItems
                }

                selectedSkill = chosenSkill

                // ✅ 關鍵修改：判斷是否為 AOE 技能
                // 假設你的 Skill model 有一個屬性 targetType 或 isAoe
                // 這裡以 targetType == SkillTarget.ALL 為例
                if (chosenSkill.isAOE) {
                    // 如果是全體技能，直接執行
                    executeAOESkillAction(chosenSkill)
                } else {
                    // 如果是單體技能，才進入 "選擇目標狀態"
                    currentState = BattleState.PLAYER_SKILL_SELECTION
                    updateBattleLog("已選擇「${chosenSkill.name}」，請點擊敵人施放！")
                }
            }
            .setNegativeButton("取消") { _, _ ->
                actionMenuLayout.visibility = View.VISIBLE
            }
            .setOnCancelListener {
                actionMenuLayout.visibility = View.VISIBLE
            }
            .show()
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
        updateBattleLog("--- 敵人回合開始 ---")

        // ✅ 使用協程來處理延遲與依序執行
        lifecycleScope.launch {
            // 1. 從 Engine 取得所有活著的敵人
            val enemies = BattleEngine.getEnemyList()

            for (enemyCell in enemies) {
                // 檢查：如果敵人已經死了（可能被機關殺死等），跳過
                if (enemyCell.enemy == null) continue

                // 2. 讓這隻敵人行動
                val result = BattleEngine.processSingleEnemyAction(enemyCell, currentPlayerStats)

                // 3. 更新 UI
                if (result.movedPositions.isNotEmpty()) {
                    adapter.updateMultipleTiles(result.movedPositions)
                }
                if (result.log.isNotEmpty()) {
                    updateBattleLog(result.log.trim())
                }

                // 4. 扣血
                if (result.playerAttacked) {
                    currentPlayerHp -= result.totalDamage
                    if (currentPlayerHp < 0) currentPlayerHp = 0
                    updateStatusUI()

                    // 播放受傷音效 (可選)
                    // EffectSoundManager.play(R.raw.sound_hit)
                }

                // 5. ✅ 關鍵：暫停 0.8 秒再換下一隻
                delay(800)

                // 檢查玩家是否死亡 (如果死了就不用讓後面敵人動了)
                if (currentPlayerHp == 0) break
            }

            // 所有敵人都動完了
            if (currentPlayerHp == 0) {
                handleBattleEnd(false)
            } else {
                currentState = BattleState.PLAYER_TURN_START
                updateBattleLog("--- 你的回合 ---")
                updateUIForTurn()
            }
        }
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

    // 執行全體(AOE)技能邏輯
    private fun executeAOESkillAction(skill: Skill) {
        // 1. 先扣除 MP 並更新 UI
        currentPlayerMp -= skill.mpCost
        updateStatusUI()
        actionMenuLayout.visibility = View.GONE // 隱藏選單

        // ------------------------------------------------------------------
        // [步驟 1] 定義「傷害結算邏輯」 (這個函式會在特效播完後被呼叫)
        // ------------------------------------------------------------------
        val performDamageLogic = {
            // 取得所有活著的敵人
            val activeEnemies = BattleEngine.getEnemyList().filter { it.enemy != null }

            if (activeEnemies.isEmpty()) {
                updateBattleLog("戰場上沒有敵人了...")
                startEnemyTurn()
            } else {
                var killCount = 0

                // 遍歷所有敵人進行傷害計算
                activeEnemies.forEach { cell ->
                    // 找出該敵人在 Adapter (UI) 中的位置
                    val position = adapter.boardData.indexOf(cell)
                    val enemy = cell.enemy!!

                    // 計算傷害
                    val damage = BattleDamageLogic.calculateSkillDamage(
                        attackerAtk = currentPlayerStats.attack,
                        defenderDef = enemy.defense,
                        multiplier = skill.multiplier
                    )

                    // 扣血邏輯
                    val currentHp = enemy.currentHp ?: enemy.health
                    val newHp = (currentHp - damage).coerceAtLeast(0)

                    // ✅ 關鍵：寫入新血量到物件中
                    enemy.currentHp = newHp

                    updateBattleLog("對 ${enemy.name} 造成 $damage 點傷害！")

                    // ✅ 關鍵：強制通知 Adapter 更新該格子的 UI (包含血條)
                    // 使用 runOnUiThread 確保 UI 更新不會因為背景執行緒而失效
                    runOnUiThread {
                        if (position != -1) {
                            adapter.notifyItemChanged(position)
                        }
                    }

                    // 處理死亡
                    if (newHp <= 0) {
                        cell.enemy = null // 移除敵人數據
                        cell.type = CellType.EMPTY

                        runOnUiThread {
                            adapter.notifyItemChanged(position) // 更新格子變空
                        }
                        updateBattleLog("${enemy.name} 被消滅了！")
                        killCount++
                    }
                }

                // 通知 Engine 清理死掉的敵人 (資料層)
                BattleEngine.refreshActiveEnemies()

                // 更新 UI 上的敵人數量文字
                enemyStatusSummary.text = "敵人數量：${BattleEngine.getActiveEnemiesCount()}"

                // 檢查勝利條件
                if (BattleEngine.getActiveEnemiesCount() == 0) {
                    handleBattleEnd(true)
                } else {
                    // 沒贏的話，換敵人回合
                    startEnemyTurn()
                }
            }
        }

        // ------------------------------------------------------------------
        // [步驟 2] 播放特效 (這裡整合了您要的「5~8次隨機打擊」)
        // ------------------------------------------------------------------
        if (skill.aoeEffectType != AoeEffectType.NONE) {
            updateBattleLog("施放全體絕學：${skill.name}！")

            // A. 播放全螢幕粒子特效 (如隕石雨) -> 播完後執行 performDamageLogic
            effectManager.playAoeEffect(skill.aoeEffectType) {
                performDamageLogic()
            }

            // B. 同步製造「亂數打擊感」 (敵人身上的小爆炸)
            lifecycleScope.launch {
                val hitEffect = skill.targetEffectResId ?: R.drawable.effect_explosion
                val activeEnemies = BattleEngine.getEnemyList().filter { it.enemy != null }

                if (activeEnemies.isNotEmpty()) {
                    // ✅ 這裡就是您要的：隨機模擬 5~8 次打擊
                    // 讓畫面看起來很忙、很華麗
                    val hits = (5..8).random()

                    repeat(hits) {
                        // 確保敵人還活著 (防呆)
                        if (activeEnemies.isNotEmpty()) {
                            // 隨機挑選一個倒楣的敵人 (可能重複打中同一隻，製造混亂感)
                            val randomEnemyCell = activeEnemies.random()
                            val position = adapter.boardData.indexOf(randomEnemyCell)

                            // 在他頭上放個特效
                            if (position != -1) {
                                playEffectOnCell(position, hitEffect)
                            }
                        }

                        // 隨機間隔 100~300ms，製造錯落有致的爆炸感
                        delay((100..300).random().toLong())
                    }
                }
            }
        } else {
            // 如果這招沒有特效，直接執行傷害邏輯
            performDamageLogic()
        }
    }

    // 在指定格子上播放特效
    private fun playEffectOnCell(position: Int, effectResId: Int) {
        // 1. 找到該位置的 ViewHolder
        // 注意：如果該位置不在螢幕範圍內 (Scroll出去了)，這可能會回傳 null，所以要用 ?.
        val holder = chessboard.findViewHolderForAdapterPosition(position) as? com.example.kappacultivationmobile.battle.ui.BattleTileViewHolder

        // 2. 執行播放
        holder?.playEffect(effectResId)
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
        BattleEngine.clear()
        BgmManager.stop()
    }
}