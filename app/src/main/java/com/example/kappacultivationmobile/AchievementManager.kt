package com.example.kappacultivationmobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AchievementManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("achievement_data", Context.MODE_PRIVATE)

    private val gson = Gson()
    private var allAchievements: List<AchievementDefinition> = emptyList()
    private var unlockedIds: MutableSet<String> = mutableSetOf()

    init {
        loadAchievementsFromJson()
        loadUnlockedAchievements()
    }

    private fun loadAchievementsFromJson() {
        try {
            val jsonString = context.assets.open("achievements.json")
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<AchievementDefinition>>() {}.type
            allAchievements = gson.fromJson(jsonString, type)
        } catch (e: Exception) {
            Log.e("AchievementManager", "讀取 achievements.json 失敗: ${e.message}")
        }
    }

    private fun loadUnlockedAchievements() {
        unlockedIds = sharedPreferences.getStringSet("unlocked_achievements", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun getAllAchievements(): List<AchievementDefinition> = allAchievements

    fun getUnlockedAchievements(): List<AchievementDefinition> =
        allAchievements.filter { unlockedIds.contains(it.id) }

    fun getLockedAchievements(): List<AchievementDefinition> =
        allAchievements.filter { !unlockedIds.contains(it.id) }

    fun isUnlocked(id: String): Boolean = unlockedIds.contains(id)

    fun markAsUnlocked(id: String) {
        if (!unlockedIds.contains(id)) {
            unlockedIds.add(id)
            sharedPreferences.edit().putStringSet("unlocked_achievements", unlockedIds).apply()
        }
    }

    fun checkAllConditions(state: GameState) {
        for (achievement in allAchievements) {
            if (!unlockedIds.contains(achievement.id) && achievement.isConditionMet(state)) {
                markAsUnlocked(achievement.id)
                Log.i("AchievementManager", "成就解鎖：${achievement.name}")
            }
        }
    }
}

// ✅ 成就資料格式（通用條件格式）

data class AchievementDefinition(
    val id: String,
    val name: String,
    val description: String,
    val rarity: String,
    val type: String,
    val condition: Map<String, Map<String, Any>>
) {
    fun isConditionMet(state: GameState): Boolean {
        val stateMap = state.toMap()
        for ((key, rule) in condition) {
            val actual = stateMap[key] ?: continue
            for ((op, targetRaw) in rule) {
                val target = (targetRaw as? Double)?.toInt() ?: continue
                val ok = when (op) {
                    "gte" -> actual >= target
                    "lte" -> actual <= target
                    "eq" -> actual == target
                    "lt" -> actual < target
                    "gt" -> actual > target
                    else -> false
                }
                if (!ok) return false
            }
        }
        return true
    }
}

// ✅ 提供目前玩家統計資訊（可來自 SharedPreferences）
data class GameState(
    val steps: Int,
    val feed_times: Int,
    val clean_times: Int,
    val gold: Int,
    val mood: Int,
    val energy: Int,
    val hunger: Int,
    val cleanliness: Int,
    val event_triggered: Int,
    val battle_wins: Int
) {
    fun toMap(): Map<String, Int> = mapOf(
        "steps" to steps,
        "feed_times" to feed_times,
        "clean_times" to clean_times,
        "gold" to gold,
        "mood" to mood,
        "energy" to energy,
        "hunger" to hunger,
        "cleanliness" to cleanliness,
        "event_triggered" to event_triggered,
        "battle_wins" to battle_wins
    )
}
