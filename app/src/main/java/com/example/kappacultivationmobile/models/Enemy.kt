package com.example.kappacultivationmobile.models

import java.io.Serializable

// 根據您的需求，Enemy 應該包含所有戰鬥所需屬性
data class Enemy(
    val id: Int,              // 改為 Int，以對應 JSON 中的數字 ID
    val name: String,         // 敵人名稱
    val level: Int,           // 敵人等級 (用於 BattleTileViewHolder 顯示)
    val health: Int,          // 總血量
    var currentHp: Int? = null, // 建議新增：用於記錄戰鬥中的當前血量 (初始可為 null)
    val attack: Int,          // 攻擊力
    val defense: Int,         // 防禦力
    val skills: List<String>?, // 修改為可為空，對應 JSON 中的技能列表
    val image: String         // 圖片檔名
) : Serializable // 確保資料可以在 Intent 間傳遞