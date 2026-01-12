package com.example.kappacultivationmobile

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object MarketInventory {
    private const val FILE_NAME = "market_inventory.json"
    private val gson = Gson()
    private var inventory: MutableMap<String, Int> = mutableMapOf()

    fun init(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<MutableMap<String, Int>>() {}.type
                inventory = gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("MarketInventory", "讀取失敗：${e.message}")
            }
        }
    }

    fun save(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(gson.toJson(inventory))
    }

    fun getItemQuantity(itemId: String): Int {
        return inventory[itemId] ?: 0
    }

    fun setItemQuantity(itemId: String, quantity: Int, context: Context) {
        inventory[itemId] = quantity.coerceAtMost(500)
        save(context)
    }

    fun decreaseItem(itemId: String, amount: Int, context: Context) {
        val current = inventory[itemId] ?: 0
        inventory[itemId] = (current - amount).coerceAtLeast(0)
        save(context)
    }

    fun increaseItem(itemId: String, amount: Int, context: Context) {
        val current = inventory[itemId] ?: 0
        inventory[itemId] = (current + amount).coerceAtMost(500)
        save(context)
    }

    fun getAllItems(): Map<String, Int> = inventory.toMap()
}
