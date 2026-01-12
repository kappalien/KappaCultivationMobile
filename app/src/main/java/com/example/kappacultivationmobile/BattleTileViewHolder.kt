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

    init {
        // 設置點擊事件，將點擊的位置傳遞給 Adapter/Activity 處理
        itemView.setOnClickListener {
            onClick(adapterPosition)
        }
    }

    fun bind(cell: BattleCell) {
        // 1. 重置並設置背景
        cellBackground.setImageResource(R.drawable.ic_tile_grass) // 假設這是草地圖塊
        cellBackground.colorFilter = null // 清除所有濾鏡

        // 2. 處理高亮狀態
        when (cell.type) {
            CellType.HIGHLIGHT_MOVE -> {
                // 藍色高亮（假設 R.color.highlight_move 存在）
                cellBackground.setColorFilter(ContextCompat.getColor(itemView.context, R.color.highlight_move), android.graphics.PorterDuff.Mode.MULTIPLY)
            }
            CellType.HIGHLIGHT_ATTACK -> {
                // 紅色高亮（假設 R.color.highlight_attack 存在）
                cellBackground.setColorFilter(ContextCompat.getColor(itemView.context, R.color.highlight_attack), android.graphics.PorterDuff.Mode.MULTIPLY)
            }
            else -> {
                // 移除顏色濾鏡
                cellBackground.colorFilter = null
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
                    // 根據敵人數據載入圖片
                    val resId = itemView.context.resources.getIdentifier(
                        enemy.image.replace(".png", ""),
                        "drawable",
                        itemView.context.packageName
                    )
                    unitImage.setImageResource(if (resId != 0) resId else R.drawable.enemy_default)
                    unitStatusText.visibility = View.VISIBLE
                    unitStatusText.text = "${enemy.name}" // 顯示敵人名稱
                    // unitStatusText.text = "Lv.${enemy.level}" // 顯示敵人等級
                }
            }
            else -> { /* 空白或障礙物 */ }
        }
    }
}