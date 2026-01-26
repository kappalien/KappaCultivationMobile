package com.example.kappacultivationmobile.battle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.util.Log
import com.example.kappacultivationmobile.models.AoeEffectType
import kotlin.random.Random

/**
 * 負責處理全螢幕粒子特效
 */
class BattleEffectManager(
    private val context: Context,
    private val container: ViewGroup // 特效要顯示在哪個容器裡
) {
    private val handler = Handler(Looper.getMainLooper())
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels

    // 快取圖片資源 ID
    private val effectCache = mutableMapOf<String, List<Int>>()

    /**
     * 根據前綴字串 (如 effect_meteor) 自動抓取 effect_meteor_1, effect_meteor_2...
     */
    private fun getEffectFrames(prefix: String): List<Int> {
        // 如果快取有，直接回傳 (效能最高)
        if (effectCache.containsKey(prefix)) {
            return effectCache[prefix]!!
        }

        val frames = mutableListOf<Int>()
        var index = 1
        val packageName = context.packageName

        while (true) {
            // 自動拼湊檔名：例如 prefix="effect_meteor" -> "effect_meteor_1"
            val resName = "${prefix}_${index}"

            // 動態查找 ID
            val resId = context.resources.getIdentifier(resName, "drawable", packageName)

            if (resId == 0) break // 找不到就停止

            frames.add(resId)
            index++
        }

        // 存入快取並回傳
        effectCache[prefix] = frames
        return frames
    }

    /**
     * 內部核心播放邏輯
     * 修改點：參數改為接收 AoeEffectType，以便傳遞給 spawnParticle
     */
    private fun playEffect(type: AoeEffectType, onComplete: () -> Unit = {}) {
        // 先取得對應的檔名前綴
        val prefix = getPrefixForType(type)
        val frames = getEffectFrames(prefix)

        if (frames.isEmpty()) {
            // 防呆：如果找不到圖片，直接執行回呼，避免卡住戰鬥流程
            Log.e("EffectManager", "找不到特效資源: $prefix")
            onComplete()
            return
        }

        val duration = 2000L // 特效總持續時間 (毫秒)
        val particleCount = 40 // 產生多少個粒子

        // 根據類型設定參數
        for (i in 0 until particleCount) {
            // 錯開生成時間，製造連續感
            val delay = Random.nextLong(0, 1000)

            handler.postDelayed({
                // ✅ 修正點 1: 現在這裡讀得到 type 了
                spawnParticle(type)
            }, delay)
        }

        // 時間到後通知戰鬥系統結算傷害
        handler.postDelayed({
            // ✅ 修正點 2: 修正變數名稱錯誤 (原為 onFinished)
            onComplete()
        }, duration)
    }

    /**
     * 根據 AOE 特效類型，取得對應的圖片檔名前綴。
     * 用於 ResourceLoader 動態拼湊檔名，例如：前綴 + "_1" -> 最終檔名
     */
    private fun getPrefixForType(type: AoeEffectType): String {
        return when(type) {
            // 火屬性 (天火流星) -> 對應檔名範例: aoe_effect_meteor_1.png
            AoeEffectType.METEOR_SHOWER -> "aoe_effect_meteor"

            // 冰屬性 (冰封千里) -> 對應檔名範例: aoe_effect_snowflake_1.png
            AoeEffectType.BLIZZARD -> "aoe_effect_snowflake"

            // 水屬性 (翻江倒海) -> 對應檔名範例: aoe_effect_rain_1.png
            AoeEffectType.HEAVY_RAIN -> "aoe_effect_rain"

            // 劍屬性 (萬劍歸宗) -> 對應檔名範例: aoe_effect_sword_1.png
            AoeEffectType.SWORD_RAIN -> "aoe_effect_sword"

            // 預設值 (防呆) -> 若傳入未定義類型，使用通用特效 (effect_generic_1.png)
            else -> "aoe_effect_generic"
        }
    }

    // 公開給外部呼叫的介面
    fun playAoeEffect(type: AoeEffectType, onComplete: () -> Unit) {
        // 直接轉送給私有的 playEffect
        playEffect(type, onComplete)
    }

    private fun spawnParticle(type: AoeEffectType) {
        val imageView = ImageView(context)

        // 1: 獲取正確的前綴名稱
        val prefix = getPrefixForType(type)

        // 2: 使用 getEffectFrames 取得 ID 清單
        val frames = getEffectFrames(prefix)

        if (frames.isEmpty()) {
            return // 如果沒圖，就不生成粒子
        }

        // 3: 從清單中隨機選一張圖作為粒子
        val resId = frames.random()
        imageView.setImageResource(resId)

        // 設定粒子大小 (隨機)
        val size = Random.nextInt(80, 150)
        val params = FrameLayout.LayoutParams(size, size)
        imageView.layoutParams = params

        // 2. 設定起始位置
        // 注意：為了讓隕石(右上往左下)不要一出生就超出左邊界，我們可以讓隕石稍微偏右生成
        if (type == AoeEffectType.METEOR_SHOWER) {
            imageView.x = Random.nextFloat() * screenWidth + 200 // 允許生成在畫面右側外面一點點
        } else {
            imageView.x = Random.nextFloat() * screenWidth
        }
        imageView.y = -300f // 從螢幕上方外面開始

        // 加入容器
        container.addView(imageView)

        // 3. 根據類型設定不同的動畫行為
        when (type) {
            // ✅ 需求1: 劍 - 指定速度 (例如 500毫秒，越小越快)
            AoeEffectType.SWORD_RAIN -> animateSword(imageView, 500L)

            // ✅ 需求2: 火 - 右上往左下
            AoeEffectType.METEOR_SHOWER -> animateMeteor(imageView)

            AoeEffectType.BLIZZARD -> animateSnow(imageView)
            AoeEffectType.HEAVY_RAIN -> animateRain(imageView)
            else -> {}
        }
    }

    // --- 各屬性動畫邏輯 ---

    // 1. 劍：垂直快速落下，帶有旋轉指向下方
    private fun animateSword(view: View, speedDuration: Long) {
        // view.rotation = 180f // 如果您的劍圖片原圖是朝上的，請取消註解這行來讓它朝下

        view.animate()
            .translationY(screenHeight.toFloat() + 200)
            .setDuration(speedDuration) // 使用傳入的參數控制速度
            .setInterpolator(AccelerateInterpolator()) // 加速落下效果
            .withEndAction { container.removeView(view) }
            .start()
    }

    // 2. 火：右上 往 左下 衝擊
    private fun animateMeteor(view: View) {
        // 設定「往左」移動的距離 (例如往左飛 600~900 像素)
        val moveLeftDistance = Random.nextInt(600, 900).toFloat()

        // 終點 X = 起點 X - 移動距離 (減法就是往左)
        val endX = view.x - moveLeftDistance

        // 圖片角度調整：
        // 您提供的圖片本身就是「頭在左下，尾在右上」的斜向圖
        // 所以 rotation 設為 0 (不旋轉) 或是微調即可符合移動軌跡
        view.rotation = 0f

        view.animate()
            .translationY(screenHeight.toFloat() + 200) // 往下 (直到超出螢幕底部)
            .translationX(endX)                         // 往左
            .setDuration(Random.nextLong(600, 1000))    // 設定飛行時間 (速度)
            .setInterpolator(LinearInterpolator())      // 等速移動
            .withEndAction { container.removeView(view) }
            .start()
    }

    // 3. 冰：緩慢飄落，帶有旋轉與透明度變化
    private fun animateSnow(view: View) {
        val endX = view.x + Random.nextInt(-200, 200) // 隨機左右飄
        view.alpha = 0.8f
        view.animate()
            .translationY(screenHeight.toFloat() + 200)
            .translationX(endX)
            .rotation(Random.nextFloat() * 360) // 旋轉
            .setDuration(Random.nextLong(2000, 3000)) // 比較慢
            .setInterpolator(LinearInterpolator())
            .withEndAction { container.removeView(view) }
            .start()
    }

    // 4. 水：極快落下，稍微傾斜
    private fun animateRain(view: View) {
        view.rotation = 10f
        view.alpha = 0.6f
        view.scaleX = 0.5f // 拉長變細
        view.scaleY = 1.5f
        view.animate()
            .translationY(screenHeight.toFloat() + 200)
            .translationX(view.x - 50)
            .setDuration(Random.nextLong(400, 600)) // 極快
            .setInterpolator(LinearInterpolator())
            .withEndAction { container.removeView(view) }
            .start()
    }
}