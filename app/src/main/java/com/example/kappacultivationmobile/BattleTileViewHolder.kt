package com.example.kappacultivationmobile.battle.ui

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.widget.ProgressBar
import com.example.kappacultivationmobile.R
import com.example.kappacultivationmobile.battle.model.BattleCell
import com.example.kappacultivationmobile.battle.model.CellType

class BattleTileViewHolder(itemView: View, private val onClick: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {

    private val cellBackground: ImageView = itemView.findViewById(R.id.cellBackground)
    private val unitImage: ImageView = itemView.findViewById(R.id.unitImage)
    private val unitStatusText: TextView = itemView.findViewById(R.id.unitStatusText)

    val effectOverlay: ImageView = itemView.findViewById(R.id.effectOverlay)

    init {
        // 設置點擊事件，將點擊的位置傳遞給 Adapter/Activity 處理
        itemView.setOnClickListener {
            onClick(adapterPosition)
        }
    }

    fun bind(cell: BattleCell) {
        // 1. 重置並設置背景
        cellBackground.setBackgroundResource(R.drawable.bg_battle_grid)
        cellBackground.setImageResource(0) // 清除前景圖片 (確保沒有殘留)
        cellBackground.colorFilter = null  // 清除濾鏡

        // 2. 處理高亮狀態
        when (cell.type) {
            CellType.HIGHLIGHT_MOVE -> {
                // 藍色高亮：直接設定背景色
                cellBackground.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.highlight_move))
            }
            CellType.HIGHLIGHT_ATTACK -> {
                // 紅色高亮：直接設定背景色
                cellBackground.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.highlight_attack))
            }
            else -> {
                // 一般狀態：不做事，維持上面的 bg_battle_grid
            }
        }

        val hpBar = itemView.findViewById<ProgressBar>(R.id.enemyHpBar) // 👈 確保 ID 正確

        if (cell.type == CellType.ENEMY && cell.enemy != null) {
            val enemy = cell.enemy!!
            hpBar.visibility = View.VISIBLE
            hpBar.max = enemy.health
            hpBar.progress = enemy.currentHp ?: enemy.health // ✅ 顯示敵人當前血量
        } else {
            hpBar.visibility = View.GONE
        }

        // 3. 處理單位圖像與狀態
        unitImage.visibility = View.GONE
        unitStatusText.visibility = View.GONE

        when (cell.type) {
            CellType.PLAYER -> {
                unitImage.visibility = View.VISIBLE
                // 假設玩家角色圖片 ID 為 R.drawable.player_unit
                unitImage.setImageResource(R.drawable.battle_player_unit)
                unitStatusText.visibility = View.VISIBLE
                unitStatusText.text = "Player"
            }
            CellType.ENEMY -> {
                unitImage.visibility = View.VISIBLE
                cell.enemy?.let { enemy ->
                    // 1. 先去掉 ".png" (變成 "alien")
                    val rawName = enemy.image.replace(".png", "")

                    // 2. 加上前綴 (變成 "enemy_alien")
                    val resourceName = "enemy_$rawName"

                    // 3. 搜尋資源 ID (尋找 R.drawable.enemy_alien)
                    val resId = itemView.context.resources.getIdentifier(
                        resourceName,
                        "drawable",
                        itemView.context.packageName
                    )

                    // 4. 設定圖片 (如果找不到就顯示預設圖)
                    unitImage.setImageResource(if (resId != 0) resId else R.drawable.enemy_default)

                    unitStatusText.visibility = View.VISIBLE
                    unitStatusText.text = "${enemy.name}"
                }
            }
            else -> { /* 空白或障礙物 */ }
        }
    }

    fun playEffect(resId: Int) {
        effectOverlay.setImageResource(resId)
        effectOverlay.visibility = View.VISIBLE

        // 簡單的閃爍動畫 (出現 -> 0.3秒後消失)
        effectOverlay.alpha = 1f
        effectOverlay.animate()
            .alpha(0f)
            .setDuration(300) // 特效持續 0.3 秒
            .withEndAction {
                effectOverlay.visibility = View.GONE
                effectOverlay.alpha = 1f // 重置透明度
            }
            .start()
    }
}