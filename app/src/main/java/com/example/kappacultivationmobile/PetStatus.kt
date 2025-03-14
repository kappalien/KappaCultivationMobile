package com.example.kappacultivationmobile

data class PetStatus(
    var hunger: Int = 100,       // 飢餓值（0~100）
    var energy: Int = 100,       // 能量值（0~100）
    var mood: Int = 100,         // 心情（0~100）
    var cleanliness: Int = 100   // 清潔度（0~100）
) {
    fun decreaseHunger() {
        val randomLoss = (3..7).random() // 🔹 讓飢餓扣除 3~7 隨機數值
        hunger = (hunger - randomLoss).coerceAtLeast(0)
    }

    fun decreaseEnergy() {
        val randomLoss = (2..5).random() // 🔹 能量扣 2~5 隨機
        energy = (energy - randomLoss).coerceAtLeast(0)
    }

    fun decreaseMood() {
        val randomLoss = (1..4).random() // 🔹 心情扣 1~4 隨機
        mood = (mood - randomLoss).coerceAtLeast(0)
    }

    fun decreaseCleanliness() {
        val randomLoss = (3..6).random() // 🔹 清潔扣 3~6 隨機
        cleanliness = (cleanliness - randomLoss).coerceAtLeast(0)
    }

    fun isUnhealthy(): Boolean {
        return hunger < 20 || energy < 20 || cleanliness < 20
    }
}
