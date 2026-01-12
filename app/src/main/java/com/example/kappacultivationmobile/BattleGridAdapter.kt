package com.example.kappacultivationmobile.battle.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kappacultivationmobile.R
import com.example.kappacultivationmobile.battle.model.BattleCell

class BattleGridAdapter(
    val boardData: MutableList<BattleCell>, // 使用 MutableList 方便數據更新
    private val onTileClicked: (Int) -> Unit // 點擊回調函數
) : RecyclerView.Adapter<BattleTileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BattleTileViewHolder {
        // 假設單個格子佈局檔案名為 item_battle_tile.xml
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_battle_tile, parent, false)
        return BattleTileViewHolder(view, onTileClicked)
    }

    override fun onBindViewHolder(holder: BattleTileViewHolder, position: Int) {
        holder.bind(boardData[position])
    }

    override fun getItemCount(): Int = boardData.size

    /**
     * 更新單一格子：常用於單位移動或狀態變更。
     * @param position 要更新的格子在列表中的索引。
     */
    fun updateTile(position: Int) {
        notifyItemChanged(position)
    }

    /**
     * 批量更新多個格子：常用於高亮範圍或特效。
     * @param positions 要更新的格子索引列表。
     */
    fun updateMultipleTiles(positions: List<Int>) {
        // 為了性能，可以使用 DiffUtil，但在小規模更新中 notifyItemChanged 也可以
        positions.forEach { position ->
            notifyItemChanged(position)
        }
    }
}