package com.example.kappacultivationmobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.util.Log

class BackpackAdapter(private val items: MutableList<Item>, private val onItemClick: (Item) -> Unit) :
    RecyclerView.Adapter<BackpackAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemName: TextView = itemView.findViewById(R.id.tvItemName)
        val itemRarity: TextView = itemView.findViewById(R.id.tvItemRarity) // ✅ 確保存在
        val itemValue: TextView = itemView.findViewById(R.id.tvItemValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_backpack, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // **顯示名稱 & 數量**
        holder.itemName.text = if (item.quantity > 1) "${item.name} x${item.quantity}" else item.name

        // **顯示稀有度**
        holder.itemRarity.text = "稀有度: ${item.rarity}"

        // **顯示價值**
        holder.itemValue.text = "價值: ${item.value} 金幣"

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size // ✅ 確保返回正確的數量
}
