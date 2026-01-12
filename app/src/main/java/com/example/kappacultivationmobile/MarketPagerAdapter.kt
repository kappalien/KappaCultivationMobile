package com.example.kappacultivationmobile

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MarketPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val items: List<Item>,
    private val onItemClick: (Item) -> Unit
) : FragmentStateAdapter(fragmentActivity) {

    private val categories = listOf(
        "全部" to null,
        "食物" to "食物",
        "清潔" to "清潔"
    )

    private val fragmentList: MutableList<MarketTabFragment?> = MutableList(categories.size) { null }

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        val (_, type) = categories[position]
        val fragment = MarketTabFragment.newInstance(type, items, onItemClick)
        fragmentList[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): MarketTabFragment? = fragmentList.getOrNull(position)

    fun getTabTitle(position: Int): String = categories[position].first
}
