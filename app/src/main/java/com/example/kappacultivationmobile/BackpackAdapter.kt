package com.example.kappacultivationmobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.widget.ScrollView
import android.util.Log

class BackpackAdapter(private val items: MutableList<Item>, private val onItemClick: (Item) -> Unit) :
    RecyclerView.Adapter<BackpackAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemName: TextView = itemView.findViewById(R.id.tvItemName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_backpack, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        if (item == null || item.itemId.isBlank()) {
            Log.e("BackpackAdapter", "跳過無效物品 at position: $position")
            return
        }

        holder.itemName.text = if (item.quantity > 1) "${item.name} * ${item.quantity}" else item.name

        // **點擊物品觸發事件**
        holder.itemView.setOnClickListener {
            onItemClick(item) // ✅ 確保不為 `null`
        }

        // **長按顯示物品詳細資訊**
        holder.itemView.setOnLongClickListener {
            showItemDetailDialog(holder.itemView.context, item)
            true
        }
    }

    private fun showItemDetailDialog(context: Context, item: Item) {
        val scrollView = ScrollView(context)
        val textView = TextView(context)

        textView.text = "名稱: ${item.name}\n描述: ${item.description}\n\n" +
                "稀有度: ${item.rarity}\n價值: ${item.value} 金幣"

        textView.setPadding(20, 20, 20, 20)
        textView.textSize = 18f

        scrollView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        scrollView.addView(textView)

        android.app.AlertDialog.Builder(context)
            .setTitle(item.name)
            .setView(scrollView)
            .setPositiveButton("確定", null)
            .show()
    }

    fun updateItems(newItems: List<Item>) {
        items.clear()
        items.addAll(newItems.filter { it.itemId.isNotBlank() }) // ✅ 過濾 null
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size // ✅ 確保返回正確的數量
}
