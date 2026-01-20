package com.example.kappacultivationmobile.models

import java.io.Serializable

data class Skill(
    val id: String,
    val name: String,
    val type: String, // "Physical" (物理), "Magic" (魔法), "Buff" (增益)
    val multiplier: Double, // 傷害倍率 (例如 1.5 代表 150% 傷害)
    val mpCost: Int,        // 消耗魔力
    val desc: String? = null // 技能描述 (可選)
) : Serializable