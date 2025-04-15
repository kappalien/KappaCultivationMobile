// BackpackTabbedActivity.kt
package com.example.kappacultivationmobile

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class BackpackTabbedActivity : AppCompatActivity() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: BackpackPagerAdapter
    private lateinit var backpack: Backpack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.backpack_tabs)

        backpack = Backpack(this)

        tabLayout = findViewById(R.id.backpackTabLayout)
        viewPager = findViewById(R.id.backpackViewPager)

        adapter = BackpackPagerAdapter(this, backpack) { item ->
            showItemOptions(item)
        }
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
    }

    private fun showItemOptions(item: Item) {
        val options = mutableListOf<String>()
        if (item.effects.isNotEmpty()) options.add("使用")
        if (item.sellable) options.add("出售")

        if (options.isEmpty()) {
            Toast.makeText(this, "這個物品無法使用或出售", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("選擇操作 - ${item.name}")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "使用" -> useItem(item.itemId)
                    "出售" -> sellItem(item.itemId)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun useItem(itemId: String) {
        val item = backpack.getItems().find { it.itemId == itemId } ?: return

        Toast.makeText(this, "你使用了 ${item.name}", Toast.LENGTH_SHORT).show()

        backpack.removeItem(itemId, 1) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun sellItem(itemId: String) {
        val item = backpack.getItems().find { it.itemId == itemId } ?: return

        if (!item.sellable) {
            Toast.makeText(this, "${item.name} 無法販賣", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("出售 ${item.name}")

        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "輸入出售數量 (最多 ${item.quantity})"

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(48, 24, 48, 0)
        container.addView(input)

        dialogBuilder.setView(container)
        dialogBuilder.setPositiveButton("出售") { dialog, _ ->
            val sellAmount = input.text.toString().toIntOrNull() ?: 1
            if (sellAmount in 1..item.quantity) {
                val goldEarned = item.value * sellAmount
                val sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val currentGold = sharedPreferences.getInt("player_gold", 0)
                val newGold = currentGold + goldEarned
                sharedPreferences.edit().putInt("player_gold", newGold).apply()

                backpack.removeItem(itemId, sellAmount) {
                    Toast.makeText(this, "售出 ${item.name} x$sellAmount，獲得 $goldEarned 金幣！", Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                }
            } else {
                Toast.makeText(this, "請輸入有效的出售數量！", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialogBuilder.setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
        dialogBuilder.show()
    }
}
