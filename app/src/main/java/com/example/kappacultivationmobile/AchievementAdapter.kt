package com.example.kappacultivationmobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AchievementAdapter(
    private val items: MutableList<AchievementDefinition>,
    private val onItemClick: (AchievementDefinition) -> Unit
) : RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvAchievementName)
        val description: TextView = view.findViewById(R.id.tvAchievementDescription)
        val icon: ImageView = view.findViewById(R.id.ivAchievementIcon)
        val rarity: TextView = view.findViewById(R.id.tvAchievementRarity)
        val unlockedStar: ImageView = view.findViewById(R.id.ivAchievementUnlocked)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.description.text = item.description
        holder.rarity.text = item.rarity

        val iconRes = when (item.rarity) {
            "稀有" -> R.drawable.ic_star_gold
            "普通" -> R.drawable.ic_star_gray
            "傳說" -> R.drawable.ic_star_legend
            else -> R.drawable.ic_star_gray
        }
        holder.icon.setImageResource(iconRes)

        val isUnlocked = AchievementManager(holder.itemView.context).isUnlocked(item.id)
        holder.unlockedStar.visibility = if (isUnlocked) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onItemClick(item) }
    }
}
