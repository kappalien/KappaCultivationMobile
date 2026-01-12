package com.example.kappacultivationmobile

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MarketActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var marketItems: List<Item>
    private lateinit var backpack: Backpack
    private lateinit var shopkeeperDialog: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market)

        BgmManager.play(this, R.raw.bgm_market, "Market") // 商城背景音樂

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        backpack = Backpack(this)

        MarketInventory.init(this) // 初始化商城庫存資料

        // 載入商品資料（來自 herbs.json + treasures.json）
        val herbs = loadItemsFromJson("herbs.json")
        val treasures = loadItemsFromJson("treasures.json")

        marketItems = (herbs + treasures)
            .distinctBy { it.itemId }
            .onEach { item ->
                if (MarketInventory.getItemQuantity(item.itemId) == 0) {
                    MarketInventory.setItemQuantity(item.itemId, 30, this)
                }
            }
            .filter { MarketInventory.getItemQuantity(it.itemId) > 0 }
            .sortedBy { it.name }

        // 初始化 TabLayout + ViewPager2
        tabLayout = findViewById(R.id.marketTabLayout)
        viewPager = findViewById(R.id.marketViewPager)

        val pagerAdapter = MarketPagerAdapter(this, marketItems) { item ->
            showItemDialog(item)
        }
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pagerAdapter.getTabTitle(position)
        }.attach()

        // 商場主人台詞
        shopkeeperDialog = findViewById(R.id.tv_shopkeeper_dialog)
        shopkeeperDialog.text = "歡迎來到修仙百貨～"
    }

    private fun showItemDialog(item: Item) {
        val dialogLines = listOf(
            "這可是本店熱賣商品！",
            "保證靈氣滿滿～",
            "快下手吧，這批快賣光囉！",
            "吃了能增修為喔～",
            "我自己也有收藏這款！"
        )
        shopkeeperDialog.text = dialogLines.random()

        val remaining = MarketInventory.getItemQuantity(item.itemId)
        val message = "${item.description}\n\n價格：${item.value} 金幣\n剩餘：$remaining 件"

        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage(message)
            .setPositiveButton("購買") { _, _ -> showPurchaseDialog(item) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPurchaseDialog(item: Item) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setText("1")

        AlertDialog.Builder(this)
            .setTitle("購買 ${item.name}")
            .setView(input)
            .setPositiveButton("確認購買") { _, _ ->
                val amount = input.text.toString().toIntOrNull() ?: 1
                val totalCost = item.value * amount
                val gold = sharedPreferences.getInt("player_gold", 0)
                val backpackQty = backpack.getItems().find { it.itemId == item.itemId }?.quantity ?: 0
                val marketQty = MarketInventory.getItemQuantity(item.itemId)

                if (gold < totalCost) {
                    Toast.makeText(this, "金幣不足！", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (amount > marketQty) {
                    Toast.makeText(this, "商城庫存不足！", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (backpackQty + amount > 500) {
                    Toast.makeText(this, "超過數量上限（背包+商城最多500）！", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sharedPreferences.edit().putInt("player_gold", gold - totalCost).apply()
                backpack.addItem(item.copy(quantity = amount))
                MarketInventory.decreaseItem(item.itemId, amount, this)

                Toast.makeText(this, "成功購買 ${item.name} x$amount", Toast.LENGTH_SHORT).show()
                // 刷新目前頁面
                val currentFragment = (viewPager.adapter as? MarketPagerAdapter)?.getFragment(viewPager.currentItem)
                currentFragment?.refreshItems()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadItemsFromJson(fileName: String): List<Item> {
        return try {
            val json = assets.open(fileName).bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Item>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}