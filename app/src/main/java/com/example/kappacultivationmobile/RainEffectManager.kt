package com.example.kappacultivationmobile

import android.content.Context
import android.os.Handler
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.lang.Runnable
import kotlin.random.Random

class RainEffectManager(
    private val context: Context,
    private val rootLayout: ViewGroup // 要疊雨滴的畫面區塊
) {
    private val handler = Handler()

    // 雨滴圖
    private val rainDrawables: List<Int> = listOf(
        R.drawable.raindrop_1,
        R.drawable.raindrop_2,
        R.drawable.raindrop_3
    )

    // 水花圖
    private val splashDrawables = listOf(
        R.drawable.splash_1,
        R.drawable.splash_2
    )

    private var isRaining = false

    var rainTargetY: Float = -1f  // 預設值為 -1，表示使用 rootLayout.height
    var splashY: Float = -1f

    fun startRain(dropCount: Int = 20, angle: Float = 15f) {
        if (isRaining) return
        isRaining = true

        rootLayout.post {
            repeat(dropCount) { _ ->
                handler.postDelayed({
                    createRaindrop(angle)
                }, Random.nextLong(0, 2000))
            }
        }
    }

    fun stopRain() {
        isRaining = false
        rootLayout.removeViewsInLayout(0, rootLayout.childCount)
    }

    private fun createRaindrop(angle: Float) {
        if (!isRaining) return

        val raindrop = ImageView(context)
        val drawableRes = rainDrawables.random()
        raindrop.setImageDrawable(ContextCompat.getDrawable(context, drawableRes))
        raindrop.rotation = angle + Random.nextFloat() * 10f - 5f
        raindrop.alpha = 0.7f

        val size = (20..50).random()
        raindrop.layoutParams = ViewGroup.LayoutParams(size, size)

        val width = rootLayout.width
        if (width <= 0) return // 或 return@Runnable

        raindrop.translationX = Random.nextInt(0, width).toFloat()

        raindrop.translationX = Random.nextInt(0, rootLayout.width).toFloat()
        raindrop.translationY = -100f
        rootLayout.addView(raindrop)

        val finalY = if (rainTargetY > 0) rainTargetY else rootLayout.height.toFloat()

        raindrop.animate()
            .translationY(finalY)
            .setDuration((2000..4000).random().toLong())
            .withEndAction(Runnable {
                val x = raindrop.x + raindrop.width / 2
                val finalSplashY = if (splashY > 0) splashY else finalY
                createSplash(x, finalSplashY)

                rootLayout.removeView(raindrop)
                if (isRaining) createRaindrop(angle)
            })
            .start()
    }

    private fun createSplash(x: Float, y: Float) {
        val splash = ImageView(context)
        val drawable = splashDrawables.random()

        splash.setImageDrawable(ContextCompat.getDrawable(context, drawable))
        splash.alpha = 0.8f

        val size = (40..60).random()
        splash.layoutParams = ViewGroup.LayoutParams(size, size)

        splash.translationX = x - size / 2
        splash.translationY = y - size / 2

        rootLayout.addView(splash)

        // 可選：1.5 秒後消失
        Handler().postDelayed({
            rootLayout.removeView(splash)
        }, 1500)
    }
}
