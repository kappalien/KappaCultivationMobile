package com.example.kappacultivationmobile.models

import com.example.kappacultivationmobile.R
import java.io.Serializable

data class Skill(
    val id: String,
    val name: String,
    val type: String, // "Physical" (物理), "Magic" (魔法), "Buff" (增益)
    val multiplier: Double, // 傷害倍率 (例如 1.5 代表 150% 傷害)
    val mpCost: Int,        // 消耗魔力
    val desc: String? = null, // 技能描述 (可選)
    val isAOE: Boolean = false, // 是否為全體攻擊 (預設 false)

    // 全螢幕特效資源
    val aoeEffectType: AoeEffectType = AoeEffectType.NONE,

    // 命中目標時的特效資源 ID (例如 R.drawable.effect_explosion)
    // 這裡設一個預設值，這樣就算您沒設定，打人也會有基本特效
    val targetEffectResId: Int? = R.drawable.effect_explosion
) : Serializable