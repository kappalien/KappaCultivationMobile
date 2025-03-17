package com.example.kappacultivationmobile

class Backpack {
    private val items = mutableListOf<String>()

    fun addItem(item: String) {
        items.add(item)
    }

    fun getItems(): List<String> {
        return items
    }
}
