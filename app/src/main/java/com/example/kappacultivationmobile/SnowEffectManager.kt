
package com.example.kappacultivationmobile

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.lang.Runnable
import kotlin.random.Random

class SnowEffectManager(
    private val context: Context,
    private val rootLayout: ViewGroup
) {
    private val handler = Handler()
    private val snowDrawables = listOf(
        R.drawable.snowflake_1,
        R.drawable.snowflake_2,
        R.drawable.snowflake_3
    )

    var snowTargetY: Float = -1f
    private var isSnowing = false

    private val snowLoopRunnable = object : Runnable {
        override fun run() {
            if (!isSnowing) return
            createSnowflake()
            handler.postDelayed(this, (300..600).random().toLong()) // 每 0.3 ~ 0.6 秒產生一片雪花
        }
    }

    fun startSnow() {
        if (isSnowing) return
        isSnowing = true
        handler.post(snowLoopRunnable) // 開始產雪循環
    }

    fun stopSnow() {
        isSnowing = false
        handler.removeCallbacks(snowLoopRunnable)
        rootLayout.removeAllViews()
    }

    private fun createSnowflake() {
        if (!isSnowing) return

        val flake = ImageView(context)
        val drawableRes = snowDrawables.random()
        flake.setImageDrawable(ContextCompat.getDrawable(context, drawableRes))
        flake.alpha = 0.85f

        val size = (30..60).random()
        flake.layoutParams = ViewGroup.LayoutParams(size, size)

        val width = rootLayout.width
        if (width <= 0) return

        flake.translationX = Random.nextInt(0, width).toFloat()
        flake.translationY = -100f
        rootLayout.addView(flake)

        // 雪花飄動效果
        val duration = (10000..12000).random().toLong()
        val driftX = (30..80).random().toFloat()
        val targetY = if (snowTargetY > 0) snowTargetY else rootLayout.height.toFloat()

        val xAnimator = ObjectAnimator.ofFloat(
            flake,
            "translationX",
            flake.translationX - driftX,
            flake.translationX + driftX
        ).apply {
            this.duration = 2000
            this.repeatCount = ValueAnimator.INFINITE
            this.repeatMode = ValueAnimator.REVERSE
        }

        val yAnimator = ObjectAnimator.ofFloat(
            flake,
            "translationY",
            -100f, targetY
        ).apply {
            this.duration = duration
        }

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(xAnimator, yAnimator)

        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 停止搖擺
                xAnimator.cancel()

                // 淡出後移除雪花
                flake.animate()
                    .alpha(0f)
                    .setDuration(1500)
                    .withEndAction {
                        rootLayout.removeView(flake)
                    }
                    .start()
            }
        })

        animatorSet.start()

        // 延遲移除雪花
        handler.postDelayed({
            rootLayout.removeView(flake)
        }, duration + 3000)
    }
}
