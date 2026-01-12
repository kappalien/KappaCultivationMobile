package com.example.kappacultivationmobile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MarketTabFragment : Fragment() {

    companion object {
        fun newInstance(type: String?, items: List<Item>, onItemClick: (Item) -> Unit): MarketTabFragment {
            val fragment = MarketTabFragment()
            fragment.filterType = type
            fragment.fullItemList = items
            fragment.onItemClick = onItemClick
            return fragment
        }
    }

    private var filterType: String? = null
    private lateinit var fullItemList: List<Item>
    private lateinit var onItemClick: (Item) -> Unit
    private var recyclerView: RecyclerView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_market_tab, container, false)
        recyclerView = view.findViewById(R.id.rvMarketTab)
        recyclerView?.layoutManager = GridLayoutManager(requireContext(), 3)

        loadItems()
        return view
    }

    fun refreshItems() {
        loadItems()
    }

    private fun loadItems() {
        val filtered = if (filterType == null) {
            fullItemList
        } else {
            fullItemList.filter { it.type == filterType }
        }
        recyclerView?.adapter = MarketAdapter(filtered.toMutableList(), onItemClick)
    }
}
