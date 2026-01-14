package com.example.kappacultivationmobile // 請確認這行與您的 package 名稱一致

/**
 * LevelManager (等級管理器)
 * 專門負責計算經驗值、等級成長曲線、判斷是否升級
 */
class LevelManager {

    // 使用 private set 保護數據，不讓外部隨意修改，只能透過 addExp 改變
    var currentLevel: Int = 1
        private set

    var currentExp: Long = 0
        private set

    // 設定基礎經驗值 (第1級升第2級需要多少)
    private val baseExp: Long = 100

    /**
     * 取得當前等級升級所需的總經驗值
     * 公式：100 * (2 的 (等級-1) 次方) -> 指數成長
     * Level 1: 100
     * Level 2: 200
     * Level 3: 400
     */
    fun getRequiredExp(): Long {
        // 使用位元運算 (shl) 替代 Math.pow，效能更好且適合整數
        // 注意：為了防止數值過大溢出，這裡使用 Long
        return baseExp * (1L shl (currentLevel - 1))
    }

    /**
     * 增加經驗值並處理升級邏輯
     * @param amount 獲得的經驗量
     * @return Boolean 如果有升級回傳 true，否則 false (方便 UI 彈出提示)
     */
    fun addExp(amount: Long): Boolean {
        currentExp += amount
        var leveledUp = false

        // 使用 while 迴圈，處理一次獲得大量經驗連升好幾級的情況
        while (currentExp >= getRequiredExp()) {
            currentExp -= getRequiredExp()
            currentLevel++
            leveledUp = true
        }

        return leveledUp
    }

    // 如果您需要存檔功能，可以在這裡加一個 loadData(level: Int, exp: Long) 的方法
    fun loadData(savedLevel: Int, savedExp: Long) {
        currentLevel = savedLevel
        currentExp = savedExp
    }
}