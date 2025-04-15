package com.example.kappacultivationmobile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AchievementTabFragment : Fragment() {
    companion object {
        fun newInstance(
            type: String?,
            provider: IAchievementProvider,
            onItemClick: (AchievementDefinition) -> Unit
        ): AchievementTabFragment {
            val fragment = AchievementTabFragment()
            fragment.filterType = type
            fragment.provider = provider
            fragment.onItemClick = onItemClick
            return fragment
        }
    }

    private var filterType: String? = null
    private lateinit var provider: IAchievementProvider
    private lateinit var onItemClick: (AchievementDefinition) -> Unit

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_achievement_tab, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvAchievementTab)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val items = if (filterType == null) {
            provider.getAchievements()
        } else {
            provider.getAchievements().filter { it.rarity == filterType }
        }

        recyclerView.adapter = AchievementAdapter(items.toMutableList(), onItemClick)
        return view
    }
}
