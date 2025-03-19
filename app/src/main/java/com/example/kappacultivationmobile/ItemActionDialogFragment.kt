package com.example.kappacultivationmobile

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.util.Log

class ItemActionDialogFragment(
    private val item: Item,
    private val backpack: Backpack,
    private val onItemUsed: (String) -> Unit,
    private val onItemSold: (String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // **檢查 item 是否為 null，避免崩潰**
        if (item == null) {
            Log.e("ItemActionDialog", "點擊的物品為 null")
            return Dialog(requireContext()).apply {
                setTitle("錯誤")
                setContentView(TextView(requireContext()).apply {
                    text = "無效的物品"
                })
            }
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_item_action)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvItemName = dialog.findViewById<TextView>(R.id.tvItemName)
        val tvItemDesc = dialog.findViewById<TextView>(R.id.tvItemDesc)
        val btnUse = dialog.findViewById<Button>(R.id.btnUse)
        val btnSell = dialog.findViewById<Button>(R.id.btnSell)
        val btnClose = dialog.findViewById<Button>(R.id.btnClose)

        tvItemName.text = item.name
        tvItemDesc.text = item.description

        // **使用物品**
        btnUse.setOnClickListener {
            onItemUsed(item.itemId) // ✅ 觸發 `useItem()`
            showToast("使用 ${item.name}")  // 🔹 **顯示提示**
            dismiss()
        }

        // **售出物品**
        btnSell.setOnClickListener {
            if (item.sellable) {
                onItemSold(item.itemId) // ✅ 觸發 `sellItem()`
                showToast("售出 ${item.name} 獲得 ${item.value} 金幣！")  // 🔹 **顯示提示**
                dismiss()
            } else {
                showToast("${item.name} 無法出售！")  // 🔹 **防止售出不可賣物品**
            }
        }
        btnClose.setOnClickListener { dismiss() }
        return dialog
    }

    fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

}

