package com.example.kappacultivationmobile.models

data class LevelMilestone(
    val level: Int,
    val health: Int,
    val mana: Int,
    val attack: Int,
    val defense: Int,
    val nextLevelSteps: Int,
    val skills: List<Skill> = emptyList()
)

data class Skill(
    val id: String,
    val name: String,
    val type: String,
    val multiplier: Double,
    val mpCost: Int,
    val desc: String? = null
)