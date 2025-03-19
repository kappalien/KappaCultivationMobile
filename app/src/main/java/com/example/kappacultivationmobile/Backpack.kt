package com.example.kappacultivationmobile

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

// **物品數據模型**
data class Item(
    val itemId: String,
    val name: String,
    var quantity: Int,
    val description: String,
    val rarity: String,
    val value: Int,
    val type: String,
    val effects: Map<String, Int>,
    val sellable: Boolean
)

// **背包系統**
class Backpack(private val context: Context) {
    private val fileName = "backpack.json"
    private val gson = Gson()

    private var items: MutableMap<String, Item> = loadBackpack() // **讀取背包數據**

    // **獲取所有物品**
    fun getItems(): List<Item> {
        return items.values.filterNotNull().toList()
    }

    // **新增物品（若已存在則數量 +1）**
    fun addItem(item: Item) {
        if (items.containsKey(item.itemId)) {
            items[item.itemId]!!.quantity = (items[item.itemId]!!.quantity + item.quantity).coerceAtLeast(0)
        } else {
            items[item.itemId] = item.copy(quantity = item.quantity.coerceAtLeast(0))
        }
        saveBackpack()
    }

    // **刪除物品（數量減少，為 0 則移除）**
    fun removeItem(itemId: String, amount: Int = 1, onInventoryUpdated: () -> Unit = {}) {
        if (items.containsKey(itemId)) {
            val currentItem = items[itemId]!!
            if (currentItem.quantity >= amount) {
                currentItem.quantity -= amount
                if (currentItem.quantity == 0) items.remove(itemId) // **數量歸零則刪除**
                saveBackpack()
                onInventoryUpdated()  // **通知 UI 更新**
            } else {
                Log.w("Backpack", "無法移除 ${currentItem.name}，數量不足！")
            }
        } else {
            Log.w("Backpack", "嘗試移除 `$itemId`，但該物品不存在於背包內！")
        }
    }


    // **存儲背包至 JSON**
    private fun saveBackpack() {
        val json = gson.toJson(mapOf("items" to items))
        val file = File(context.filesDir, fileName)
        file.writeText(json)
        Log.d("Backpack", "背包已保存: $json")
    }

    // **讀取 JSON（若無檔案則回傳空背包）**
    private fun loadBackpack(): MutableMap<String, Item> {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<Map<String, Item>>() {}.type
                gson.fromJson<Map<String, Item>>(json, type)?.toMutableMap() ?: mutableMapOf()
            } catch (e: Exception) {
                Log.e("Backpack", "讀取 JSON 錯誤: ${e.message}")
                mutableMapOf()  // 🔹 讀取錯誤時回傳空背包
            }
        } else {
            mutableMapOf()  // 🔹 JSON 不存在時回傳空背包
        }
    }
}
