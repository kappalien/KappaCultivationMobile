package com.example.kappacultivationmobile

import kotlin.random.Random
import android.util.Log

class CharacterResponse {
    private val happyResponses = listOf(
        "哇！今天的天氣真不錯，感覺能突破修為！💪",
        "太棒了！你再走一點，我就變強了！🔥",
        "嘿嘿，今天的我狀態很好！😆",
        "加油！我們快要突破新等級了！🚀",
        "我感覺自己變強了，繼續努力吧！✨"
    )

    private val tiredResponses = listOf(
        "啊... 好累啊，我需要休息一下！😩",
        "走這麼多路，真的不累嗎？我快累趴了！😓",
        "主人，我餓了... 有什麼靈草可以吃嗎？🍃",
        "我們是不是該稍微放慢一下速度呢？😥",
        "這麼多步數了，我是不是該晉級了？🤔"
    )

    private val levelUpResponses = listOf(
        "太棒了！我終於突破到新等級了！🎉",
        "哇！這感覺真棒，我變強了！💪",
        "主人，我感受到靈力湧入體內！🔮",
        "終於突破了！謝謝你一直帶著我修行！😄",
        "新境界，新的挑戰！讓我們繼續前進吧！🚀"
    )

    private val almostLevelUpResponses = listOf(
        "我感覺靈力在波動了！快要突破了！😲",
        "再走幾步，我應該就能升級了！🔥",
        "主人！我們快要突破新境界了！加油！😆",
        "我感覺一股強大的能量... 就差一點點！⚡",
        "這次突破後，我應該會變得更強！期待吧！😎"
    )

    fun getRandomResponseForSteps(): String {
        Log.d("CharacterResponse", "happyResponses 大小: ${happyResponses.size}, tiredResponses 大小: ${tiredResponses.size}")

        val isHappy = Random.nextBoolean()
        val response = if (isHappy) happyResponses.random() else tiredResponses.random()

        Log.d("CharacterResponse", "選擇 ${if (isHappy) "快樂" else "疲倦"} 對話: $response")
        return response
    }

    fun getLevelUpResponse(): String {
        return levelUpResponses.random()
    }

    fun getAlmostLevelUpResponse(): String {
        return almostLevelUpResponses.random()
    }
}
