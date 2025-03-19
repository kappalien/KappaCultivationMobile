package com.example.kappacultivationmobile

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.widget.Button
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BackpackDialogFragment(
    private val backpack: Backpack,
    private val onItemUsed: (String) -> Unit,
    private val onItemSold: (String) -> Unit
) : DialogFragment() {

    private lateinit var rvBackpack: RecyclerView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_backpack)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        rvBackpack = dialog.findViewById(R.id.rvBackpack)
        val btnClose = dialog.findViewById<Button>(R.id.btnClose)

        rvBackpack.layoutManager = GridLayoutManager(requireContext(), 3)
        rvBackpack.adapter = BackpackAdapter(backpack.getItems().toMutableList(), ::onItemClicked)

        btnClose.setOnClickListener { dismiss() }

        return dialog
    }

    private fun onItemClicked(item: Item) {
        if (!isAdded) return // **防止 Fragment 沒有附加時崩潰**

        ItemActionDialogFragment(item, backpack, { itemId ->
            onItemUsed(itemId)
            updateBackpackUI()
        }, { itemId ->
            onItemSold(itemId)
            updateBackpackUI()
        }).show(parentFragmentManager, "ItemActionDialog")
    }

    private fun updateBackpackUI() {
        rvBackpack.adapter?.let { adapter ->
            if (adapter is BackpackAdapter) {
                adapter.updateItems(backpack.getItems())
            }
        }
    }
}

