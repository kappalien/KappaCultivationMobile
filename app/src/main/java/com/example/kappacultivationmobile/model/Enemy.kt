package com.example.kappacultivationmobile.model

import java.io.Serializable

data class Enemy(
    val id: String,
    val name: String,
    val health: Int,
    val attack: Int,
    val defense: Int,
    val skills: List<String>,
    val image: String
) : Serializable