package com.example.kappacultivationmobile

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class BackpackPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val backpack: Backpack,
    private val onItemClick: (Item) -> Unit
) : FragmentStateAdapter(fragmentActivity) {

    private val categories = listOf(
        "全部" to null,
        "食物" to "食物",
        "清潔" to "清潔",
        "寶藏" to "寶藏"
    )

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        val (_, type) = categories[position]
        return BackpackTabFragment.newInstance(type, backpack, onItemClick)
    }

    fun getTabTitle(position: Int): String = categories[position].first
}
