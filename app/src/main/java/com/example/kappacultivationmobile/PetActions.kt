package com.example.kappacultivationmobile

class PetActions(private val petStatus: PetStatus) {

    fun feed() {
        petStatus.hunger = (petStatus.hunger + 30).coerceAtMost(100)
    }

    fun meditate() { // 修煉恢復能量
        petStatus.energy = (petStatus.energy + 40).coerceAtMost(100)
        petStatus.mood = (petStatus.mood + 10).coerceAtMost(100)
    }

    fun play() { // 娛樂提高心情
        petStatus.mood = (petStatus.mood + 25).coerceAtMost(100)
        petStatus.energy = (petStatus.energy - 10).coerceAtLeast(0) // 玩耍會消耗一點能量
    }

    fun clean() { // 洗澡或淨化
        petStatus.cleanliness = 100
    }
}
