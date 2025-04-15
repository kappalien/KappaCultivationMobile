package com.example.kappacultivationmobile

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class AchievementPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val provider: IAchievementProvider,
    private val onItemClick: (AchievementDefinition) -> Unit
) : FragmentStateAdapter(fragmentActivity) {

    val rarityOrder = listOf("普通", "稀有", "傳說")
    private val categories: List<Pair<String, String?>> =
        listOf("全部" to null) +
                rarityOrder.filter { r -> provider.getAchievements().any { it.rarity == r } }
                    .map { it to it }

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        val (_, type) = categories[position]
        return AchievementTabFragment.newInstance(type, provider, onItemClick)
    }

    fun getTabTitle(position: Int): String = categories[position].first
}
