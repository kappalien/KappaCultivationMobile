package com.example.kappacultivationmobile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BackpackTabFragment : Fragment() {
    companion object {
        fun newInstance(type: String?, backpack: Backpack, onItemClick: (Item) -> Unit): BackpackTabFragment {
            val fragment = BackpackTabFragment()
            fragment.filterType = type
            fragment.backpack = backpack
            fragment.onItemClick = onItemClick
            return fragment
        }
    }

    private var filterType: String? = null
    private lateinit var backpack: Backpack
    private lateinit var onItemClick: (Item) -> Unit

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_backpack_tab, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvBackpackTab)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

        val items = if (filterType == null) {
            backpack.getItems()
        } else {
            backpack.getItems().filter { it.type == filterType }
        }

        recyclerView.adapter = BackpackAdapter(items.toMutableList(), onItemClick)
        return view
    }
}
