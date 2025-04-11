package com.example.kappacultivationmobile

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kappacultivationmobile.model.Enemy
import com.google.gson.Gson


class BattleActivity : AppCompatActivity() {

    private lateinit var enemy: Enemy
    private lateinit var mediaPlayer: MediaPlayer

    private lateinit var enemyImageView: ImageView
    private lateinit var battleLogTextView: TextView
    private lateinit var attackButton: Button
    private lateinit var escapeButton: Button

    // 血量
    private var currentPlayerHp = 0
    private var currentEnemyHp = 0
    private lateinit var playerInfo: LevelInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle)

        enemy = intent.getSerializableExtra("enemy") as? Enemy
            ?: run {
                Toast.makeText(this, "敵人資料載入失敗", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        enemyImageView = findViewById(R.id.enemyImageView)
        battleLogTextView = findViewById(R.id.battleLogTextView)
        attackButton = findViewById(R.id.attackButton)
        escapeButton = findViewById(R.id.escapeButton)


        // 設定圖片
        val imageName = enemy.image.replace(".png", "")
        val resId = resources.getIdentifier(imageName, "drawable", packageName)
        if (resId != 0) {
            enemyImageView.setImageResource(resId)
        } else {
            Toast.makeText(this, "找不到敵人圖片：$imageName", Toast.LENGTH_SHORT).show()
        }

        // 初始化戰鬥敵我血量資訊
        val playerLevel = getSharedPreferences("app_settings", MODE_PRIVATE).getInt("currentLevel", 1)
        playerInfo = loadLevelInfo(playerLevel)

        val sp = getSharedPreferences("app_settings", MODE_PRIVATE)
        val savedHp = sp.getInt("currentHp", playerInfo.health)  // 讀取實際血量
        currentPlayerHp = savedHp
        currentEnemyHp = enemy.health

        updateHealthUI()

        // 播放戰鬥 BGM
//        mediaPlayer = MediaPlayer.create(this, R.raw.battle_bgm)
//        mediaPlayer.isLooping = true
//        mediaPlayer.start()

        // 顯示初始敵人資訊
        battleLogTextView.text = "你遭遇了 ${enemy.name}！\n準備戰鬥！"

        attackButton.setOnClickListener {
            val (playerDamage, enemyDamage) = BattleLogic.performAttack(playerInfo, enemy)
            currentEnemyHp -= playerDamage
            currentPlayerHp -= enemyDamage

            if (currentEnemyHp < 0) currentEnemyHp = 0
            if (currentPlayerHp < 0) currentPlayerHp = 0

            updateHealthUI()

            if (currentEnemyHp == 0) {
                battleLogTextView.text = "你打敗了 ${enemy.name}！"
                attackButton.isEnabled = false
                escapeButton.isEnabled = false

                savePlayerHp()

                battleLogTextView.postDelayed({
                    finish()
                }, 2000) // 等 2 秒顯示訊息再跳出
            } else if (currentPlayerHp == 0) {
                battleLogTextView.text = "${enemy.name} 打倒了你！"
                attackButton.isEnabled = false
                escapeButton.isEnabled = false

                savePlayerHp()

                battleLogTextView.postDelayed({
                    finish()
                }, 2000) // 等 2 秒顯示訊息再跳出
            } else {
                battleLogTextView.text = """
            你對 ${enemy.name} 造成了 $playerDamage 傷害！
            ${enemy.name} 反擊造成 $enemyDamage 傷害！
        """.trimIndent()
            }
        }

        // 逃跑
        val escapeButton = findViewById<Button>(R.id.escapeButton)
        escapeButton.setOnClickListener {
            Toast.makeText(this, "你成功逃跑了！", Toast.LENGTH_SHORT).show()
            savePlayerHp()
            finish()
        }
    }

    private fun savePlayerHp() {
        val sp = getSharedPreferences("app_settings", MODE_PRIVATE)
        sp.edit().putInt("currentHp", currentPlayerHp).apply()
    }

    private fun updateHealthUI() {
        findViewById<TextView>(R.id.playerHpTextView).text = "玩家 HP: $currentPlayerHp / ${playerInfo.health}"
        findViewById<ProgressBar>(R.id.playerHpBar).apply {
            max = playerInfo.health
            progress = currentPlayerHp
        }

        findViewById<TextView>(R.id.enemyHpTextView).text = "${enemy.name} HP: $currentEnemyHp / ${enemy.health}"
        findViewById<ProgressBar>(R.id.enemyHpBar).apply {
            max = enemy.health
            progress = currentEnemyHp
        }
    }

    private fun loadLevelInfo(level: Int): LevelInfo {
        val json = assets.open("level_info.json").bufferedReader().use { it.readText() }
        val list = Gson().fromJson<List<LevelInfo>>(json, object : com.google.gson.reflect.TypeToken<List<LevelInfo>>() {}.type)
        return list[level - 1]
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
    }
}
