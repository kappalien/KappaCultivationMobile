package com.example.kappacultivationmobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.util.Log
import android.view.MotionEvent
import java.lang.reflect.Type
import android.os.Looper
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import com.example.kappacultivationmobile.model.Enemy
import com.example.kappacultivationmobile.AchievementManager
import com.example.kappacultivationmobile.GameState


inline fun <reified T> typeToken() = object : TypeToken<T>() {}

enum class WeatherType {
    SUNNY, RAINY, SNOWY, NORNAML
}

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var locationListener: android.location.LocationListener
    private var isTrackingLocation = false

    private var stepCounterSensor: Sensor? = null
    private lateinit var stepCounterHelper: StepCounterHelper

    // 天氣
    private var currentWeather: WeatherType? = null
    private lateinit var rainEffectManager: RainEffectManager // 天氣管理 (下雨)
    private lateinit var snowEffectManager: SnowEffectManager // 天氣管理 (下雪)
    private val weatherHandler = android.os.Handler(Looper.getMainLooper())
    private lateinit var weatherRunnable: Runnable

    private lateinit var achievementManager: AchievementManager //  成就管理

    private lateinit var staticBackground: ImageView    // 背景
    private lateinit var tvStatus: TextView // 等級資訊
    private lateinit var petStatusTextView: TextView    //狀態資訊
    private lateinit var characterStatusIcon: ImageView //  角色狀態圖示
    private lateinit var characterImage: ImageView  // 角色

    // 角色用圖片
    private val characterImages = mapOf(
        "cool" to R.drawable.emoji_cool,
        "happy" to R.drawable.emoji_happy,
        "hungry" to R.drawable.emoji_hungry,
        "tired" to R.drawable.emoji_tired,
        "sick" to R.drawable.emoji_sick,
        "mood" to R.drawable.emoji_mood,
        "normal" to R.drawable.emoji_normal
    )

    // 角色跳動/旋轉
    private val random = java.util.Random()
    private var isAnimating = false
    private val animationDuration = 2000L // 動畫持續時間 (毫秒)
    private val jumpHeight = 20f       // 跳躍高度 (像素)
    private val maxRotationAngle = 25f  // 最大旋轉角度

    private val animationHandler = android.os.Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!isAnimating) {
                animateCharacter()
            }
            animationHandler.postDelayed(this, 5000 + random.nextInt(5000).toLong())
        }
    }

    // 角色旋轉
    private var touchStartX = 0f
    private var currentRotationY = 0f
    private val minRotationY = -25f // 允許向左旋轉的最大角度
    private val maxRotationY = 25f  // 允許向右旋轉的最大角度

    private lateinit var characterInfo: TextView
    private lateinit var characterResponseTextView: TextView
    private lateinit var characterResponse: CharacterResponse
    private lateinit var sharedPreferences: SharedPreferences

    private val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1
    private val REQUEST_LOCATION_PERMISSION = 2

    private lateinit var levelInfoList: List<LevelInfo> // 等級資訊
    private lateinit var playerInfo: LevelInfo  // 角色資訊


    // 互動功能
    private lateinit var petStatus: PetStatus
    private lateinit var petActions: PetActions
    private lateinit var petUpdateManager: PetUpdateManager
    private lateinit var randomEventManager: RandomEventManager
    private lateinit var eventNotificationTextView: TextView

    // 遭遇
    private lateinit var herbs: List<Item>
    private lateinit var treasures: List<Item>

    // 背包
    private lateinit var backpack: Backpack // 背包管理

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "com.example.kappacultivationmobile/1.0 (KappaApp)"

        super.onCreate(savedInstanceState)


        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 預設是否保持螢幕常亮
        if (sharedPreferences.getBoolean("keepScreenOn", true)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // 初始化 LocationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val gpsEnabled = sharedPreferences.getBoolean("gpsEnabled", false)
        val showOSM = sharedPreferences.getBoolean("showOSM", false)

        // 設定 Activity 的 Layout
        setContentView(R.layout.activity_main)

        // 設定 OpenStreetMap 配置
        Configuration.getInstance().userAgentValue = packageName

        staticBackground = findViewById(R.id.staticBackground)

        // 檢查是否要顯示 OSM 地圖
        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        if (showOSM) {
            mapView.visibility = View.VISIBLE
            staticBackground.visibility = View.GONE
        } else {
            mapView.visibility = View.GONE
            staticBackground.visibility = View.VISIBLE
        }

        // 預設背景
        staticBackground.setImageResource(R.drawable.background_day)

        // 天氣系統
        val weatherLayer = findViewById<ViewGroup>(R.id.weather_layer)
        val buttonArea = findViewById<View>(R.id.button_layout)

        rainEffectManager = RainEffectManager(this, weatherLayer)   // 初始化
        snowEffectManager = SnowEffectManager(this, weatherLayer)

        // 啟動天氣輪替（立刻執行一次 + 每 1 分鐘切換）
        buttonArea.post {
            changeWeather() // 第一次天氣設定，等佈局完成後再執行
            weatherRunnable = object : Runnable {
                override fun run() {
                    changeWeather()
                    weatherHandler.postDelayed(this, 5 * 60 * 1000)
                }
            }
            weatherHandler.postDelayed(weatherRunnable, 5 * 60 * 1000)
        }

        // 互動按鈕
        val btnFeed = findViewById<Button>(R.id.button_feed)
        val btnPlay = findViewById<Button>(R.id.button_play)
        val btnClean = findViewById<Button>(R.id.button_clean)
        val btnInteract = findViewById<Button>(R.id.button_interact)
        val btnAchievement = findViewById<Button>(R.id.button_achievements)

        val interactButtons = listOf(btnFeed, btnPlay, btnClean)
        interactButtons.forEach { it.visibility = View.GONE } // 初始隱藏 互動的子功能按鍵

        btnInteract.setOnClickListener {
            val newVisibility = if (btnFeed.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            interactButtons.forEach { it.visibility = newVisibility }
        }

        btnAchievement.setOnClickListener {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }

        // 設定按鈕
        val settingsButton: Button = findViewById(R.id.buttonSettings)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 初始化 UI 元件
        mapView = findViewById(R.id.mapView)
        tvStatus = findViewById(R.id.tv_status) // 等級資訊
        characterImage = findViewById(R.id.character_image) // 角色圖片


        // 角色定時動畫
        animationHandler.postDelayed(animationRunnable, 3000)

        // 處理角色旋轉事件
        characterImage.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - touchStartX
                    val rotationAngle = deltaX / 10f // 調整除數來控制旋轉速度
                    var newRotationY = currentRotationY - rotationAngle

                    // 限制旋轉角度在指定範圍內
                    if (newRotationY < minRotationY) {
                        newRotationY = minRotationY
                    } else if (newRotationY > maxRotationY) {
                        newRotationY = maxRotationY
                    }

                    view.rotationY = newRotationY
                    currentRotationY = newRotationY
                    touchStartX = event.x
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // 處理點擊事件 (如果需要)
                    if (Math.abs(event.x - touchStartX) <= 30) {
                        view.performClick()
                    }
                    true
                }

                else -> false
            }
        }

        // 處理角色點擊事件
        characterImage.setOnClickListener {
            val message = when {
                petStatus.hunger < 50 -> "我好餓...快餵我！🍖"
                petStatus.cleanliness < 50 -> "我需要洗澡啦！🛁"
                petStatus.mood < 60 -> "我今天心情不好...可以陪我玩嗎？😢"
                else -> listOf(
                    "嘿嘿～",
                    "幹嘛~~！",
                    "你好啊！",
                    "哎呀你又來了～",
                    "摸我嗎？我可是會害羞的喔///",
                    "陪我玩嘛～",
                    "你再戳我我可要反擊囉！",
                    "呼～今天心情不錯～",
                    "你回來啦！我等你好久了～",
                    "La La La ～",
                    "喵～喵～（開心地叫）",
                    "想不想聽我唱歌～？",
                    "我可是修仙界第一可愛！",
                    "快給我點好吃的嘛！",
                    "再點我一次試試看？",
                    "嘻嘻～～",
                    "有什麼寶藏要給我嗎？",
                    "我想睡覺了啦..."
                ).random()
            }
            characterResponseTextView.text = message
            characterResponseTextView.visibility = View.VISIBLE
            characterResponseTextView.postDelayed({
                characterResponseTextView.visibility = View.GONE
            }, 4000)
        }

        characterStatusIcon = findViewById(R.id.character_status_icon) // 狀態圖示
        characterInfo = findViewById(R.id.character_info)   // 角色資訊
        characterResponseTextView = findViewById(R.id.character_response)   // 角色回應

        // 初始化 locationListener
        locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                val newGeoPoint = GeoPoint(location.latitude, location.longitude)
                mapView.controller.setCenter(newGeoPoint) // 更新地圖中心
                mapView.overlays.clear() // 清除舊的標記，避免重複顯示

                val marker = Marker(mapView)
                marker.position = newGeoPoint
                marker.title = "你在這裡！"
                mapView.overlays.add(marker)

                Log.d("GPS Update", "位置更新: ${location.latitude}, ${location.longitude}")
            }

            // 從 JSON 檔案中讀取背包的物品資料，並將讀取到的資料儲存到 Backpack 類別的 items 映射中
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 讀取 SharedPreferences 存儲的數據
        val savedSteps = sharedPreferences.getInt("currentStepsInLevel", 0) // 預設為 0
        val savedLevel = sharedPreferences.getInt("currentLevel", 1) // 預設等級 1

        tvStatus.text = getString(R.string.level_and_steps, savedLevel, savedSteps) // 讀取最後一次的累加數值

        petStatusTextView = findViewById(R.id.tv_pet_status) // 讀取狀態

        // 解析 level_info.json
        val jsonString = assets.open("level_info.json").bufferedReader().use { it.readText() }
        levelInfoList = Gson().fromJson(jsonString, typeToken<List<LevelInfo>>().type)
        Log.d("CharacterInfo", "levelInfoList 解析後的大小: ${levelInfoList.size}")

        // 讀取敵人.json
        loadEnemiesFromJson()

        // 確保 JSON 正常讀取
        if (levelInfoList.isEmpty()) {
            Log.e("CharacterInfo", "levelInfoList 為空，可能 JSON 讀取失敗！")
        }

        // 初始化角色回應
        characterResponse = CharacterResponse()

        // ✅ 電子雞系統初始化
        petStatus = PetStatus()
        petActions = PetActions(petStatus)
        petUpdateManager = PetUpdateManager(petStatus) { updateUI() }

        // 初始化 StepCounterHelper
        stepCounterHelper = StepCounterHelper(
            savedSteps,
            savedLevel,
            { steps, level, response ->
                runOnUiThread {
                    tvStatus.text = getString(R.string.level_and_steps, level, steps)
                    Log.d("CharacterResponse", "更新 UI：$response")

                    characterResponseTextView.removeCallbacks(null)

                    if (response.isNotEmpty()) {
                        characterResponseTextView.text = response
                        characterResponseTextView.visibility = View.VISIBLE

                        characterResponseTextView.postDelayed({
                            if (characterResponseTextView.text == response) {
                                characterResponseTextView.visibility = View.GONE
                            }
                        }, 6000)
                    } else {
                        characterResponseTextView.visibility = View.GONE
                    }

                    updateCharacterInfo()
                }
            },
            levelInfoList,
            sharedPreferences,
            characterResponse,
            30, // 每 30 步觸發對話機率（可自訂）
            petStatus // ✅ 傳入目前的電子雞狀態
        )

        // 初始化成就管理
        achievementManager = AchievementManager(this)

        // 設定地圖
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // 是否開啟 GPS 定位
        if (gpsEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getLocationAndSetMapCenter()
            } else {
                checkPermissions() // 只有當權限真的缺少時，才請求權限
            }
        } else {
            if (::locationManager.isInitialized) {
                locationManager.removeUpdates(locationListener)
                isTrackingLocation = false
            }
        }

        // 初始化角色資訊
        updateCharacterInfo()

        // 初始化 SensorManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // ✅ 綁定 UI 按鈕
        // 餵食
        findViewById<Button>(R.id.button_feed).setOnClickListener {
            petActions.feed()
            updateUI()

            // ✅ 回血處理邏輯放這裡（不要放在 PetActions）
            val currentLevel = sharedPreferences.getInt("currentLevel", 1)
            val levelInfo = levelInfoList[currentLevel - 1]
            val maxHp = levelInfo.health

            val currentHp = sharedPreferences.getInt("currentHp", maxHp)
            val restoredHp = (maxHp * 0.2).toInt()
            val newHp = (currentHp + restoredHp).coerceAtMost(maxHp)
            sharedPreferences.edit().putInt("currentHp", newHp).apply()

            // 餵食相關成就統計用
            val feedTimes = sharedPreferences.getInt("feed_times", 0) + 1
            sharedPreferences.edit().putInt("feed_times", feedTimes).apply()

            updateCharacterInfo()
        }

        // 娛樂
        findViewById<Button>(R.id.button_play).setOnClickListener {
            petActions.play()
            updateUI()

            // ➕ 記錄娛樂次數（供成就系統用）
            val playTimes = sharedPreferences.getInt("play_times", 0) + 1
            sharedPreferences.edit().putInt("play_times", playTimes).apply()
        }

        // 清潔
        findViewById<Button>(R.id.button_clean).setOnClickListener {
            petActions.clean()
            updateUI()

            val cleanTimes = sharedPreferences.getInt("clean_times", 0) + 1
            sharedPreferences.edit().putInt("clean_times", cleanTimes).apply()
        }

        // ✅ 開始狀態變化（每 60 秒執行一次）
        petUpdateManager.startUpdating()

        // 讀取遭遇物品
        loadItemsFromJson("herbs.json", object : TypeToken<List<Item>>() {}.type) { items ->
            herbs = items
            Log.d("MainActivity", "讀取到 ${herbs.size} 種靈草")
        }
        loadItemsFromJson("treasures.json", object : TypeToken<List<Item>>() {}.type) { items ->
            treasures = items
            Log.d("MainActivity", "讀取到 ${treasures.size} 種寶藏")
        }

        // 隨機事件
        eventNotificationTextView = findViewById(R.id.tv_event_notification)

        randomEventManager = RandomEventManager { event ->
            runOnUiThread {
                eventNotificationTextView.text = event
                eventNotificationTextView.visibility = View.VISIBLE
            }
        }

        eventNotificationTextView.setOnClickListener {
            showEventList()
        }

        randomEventManager.startEventLoop() // 啟動隨機事件

        // **初始化背包**
        backpack = Backpack(this)

        // 設定 "打開背包" 按鈕
        findViewById<Button>(R.id.button_backpack).setOnClickListener {
            startActivity(Intent(this, BackpackTabbedActivity::class.java))
        }


        // 檢查權限
        checkPermissions()
    }

    // 遭遇戰鬥
    private lateinit var enemies: List<Enemy>
    private fun loadEnemiesFromJson() {
        try {
            val json = assets.open("enemies.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Enemy>>() {}.type
            enemies = Gson().fromJson(json, type)
            Log.d("EnemyData", "共載入敵人 ${enemies.size} 名")
        } catch (e: Exception) {
            Log.e("EnemyData", "讀取敵人資料失敗：${e.message}")
        }
    }

    private fun changeWeather() {
        // 先清除目前天氣
        rainEffectManager.stopRain()
        snowEffectManager.stopSnow()

        val weatherLayer = findViewById<ViewGroup>(R.id.weather_layer)
        val buttonArea = findViewById<View>(R.id.button_layout)

        when ((1..3).random()) {
            1 -> {
                Log.d("WeatherSystem", "☀️ 晴天")
                currentWeather = WeatherType.SUNNY
                staticBackground.setImageResource(R.drawable.background_sunny)
            }
            2 -> {
                Log.d("WeatherSystem", "🌧️ 雨天")
                currentWeather = WeatherType.RAINY
                staticBackground.setImageResource(R.drawable.background_rainy)
                rainEffectManager.rainTargetY = buttonArea.top.toFloat() - 70f
                rainEffectManager.splashY = buttonArea.top.toFloat() - 70f
                rainEffectManager.startRain(dropCount = 40, angle = 10f)
            }
            3 -> {
                Log.d("WeatherSystem", "❄️ 下雪")
                currentWeather = WeatherType.SNOWY
                staticBackground.setImageResource(R.drawable.background_snowy)
                snowEffectManager.snowTargetY = buttonArea.top.toFloat() - 70f
                snowEffectManager.startSnow()
            }
            4 -> {
                Log.d("WeatherSystem", "一般")
                currentWeather = WeatherType.NORNAML
                staticBackground.setImageResource(R.drawable.background_day)
            }
        }
    }

    private fun updateCharacterInfo() {
        val savedLevel = sharedPreferences.getInt("currentLevel", 1)
        if (savedLevel in 1..levelInfoList.size) {
            val levelInfo = levelInfoList[savedLevel - 1]
            val currentGold = sharedPreferences.getInt("player_gold", 0)
            val currentHp = sharedPreferences.getInt("currentHp", levelInfo.health) // 預設滿血
            characterInfo.text = getString(
                R.string.character_info_with_hp,
                levelInfo.level, currentHp, levelInfo.health,
                levelInfo.mana, levelInfo.attack, levelInfo.defense, currentGold
            )
            Log.d("CharacterInfo", "角色資訊更新: ${characterInfo.text}")
        } else {
            Log.e("CharacterInfo", "無法取得等級資訊，level: $savedLevel 超出範圍")
        }
    }

    private fun updateUI() {
        petStatusTextView.text = getString(
            R.string.pet_status,
            petStatus.energy,
            petStatus.hunger,
            petStatus.mood,
            petStatus.cleanliness
        )
        Log.d(
            "PetStatus",
            "能量: ${petStatus.energy} | 飢餓: ${petStatus.hunger} | 心情: ${petStatus.mood} | 清潔: ${petStatus.cleanliness}"
        )

        // 角色圖片切換邏輯
        val normalVariants = listOf(
            R.drawable.emoji_normal_1,
            R.drawable.emoji_normal_2,
            R.drawable.emoji_normal_3
        )

        var characterImageKey = "cool" // 預設圖片
        if (petStatus.cleanliness < 70) {
            characterImageKey = "sick"
        } else if (petStatus.hunger < 70) {
            characterImageKey = "hungry"
        } else if (petStatus.energy < 75) {
            characterImageKey = "tired"
        } else if (petStatus.mood < 60) {
            characterImageKey = "mood"
        } else if ((petStatus.hunger + petStatus.energy + petStatus.cleanliness + petStatus.mood) / 4 < 90) {
            characterImage.setImageResource(normalVariants.random())
            return
        }
        characterImage.setImageResource(characterImages[characterImageKey] ?: R.drawable.emoji_happy)

        // 取得各狀態 (成就系統需要)
        val gameState = GameState(
            steps = sharedPreferences.getInt("steps_total", 0),
            feed_times = sharedPreferences.getInt("feed_times", 0),
            clean_times = sharedPreferences.getInt("clean_times", 0),
            gold = sharedPreferences.getInt("player_gold", 0),
            mood = petStatus.mood,
            energy = petStatus.energy,
            hunger = petStatus.hunger,
            cleanliness = petStatus.cleanliness,
            event_triggered = sharedPreferences.getInt("event_triggered", 0),
            battle_wins = sharedPreferences.getInt("battle_wins", 0)
        )
        achievementManager.checkAllConditions(gameState)
    }

    private fun animateCharacter() {
        if (isAnimating) return  // 如果已經在動畫中，則不重複執行

        isAnimating = true

        val randomAnimation = random.nextInt(2) // 0: 跳動, 1: 旋轉

        if (randomAnimation == 0) {
            // 跳動動畫 (彈跳效果 - 使用 Interpolator)
            val jumpHeight = 60f
            val animationDuration = 700L

            characterImage.animate()
                .translationYBy(-jumpHeight)
                .setDuration(animationDuration / 2)
                .setInterpolator(AccelerateDecelerateInterpolator()) // 加速減速
                .withEndAction {
                    characterImage.animate()
                        .translationY(0f)
                        .setDuration(animationDuration / 2)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction { isAnimating = false }
                        .start()
                }
                .start()
        } else {
            // 旋轉動畫
            val randomRotation = random.nextFloat() * maxRotationAngle * 2 - maxRotationAngle
            val animationDuration = 700L  // 減少持續時間
            characterImage.animate()
                .rotationBy(randomRotation)
                .setDuration(animationDuration)
                .withEndAction {
                    characterImage.animate()
                        .rotationBy(-randomRotation)
                        .setDuration(animationDuration)
                        .withEndAction {
                            characterImage.animate()
                                .rotation(0f)
                                .setDuration(animationDuration / 2)
                                .withEndAction { isAnimating = false }
                                .start()
                        }
                        .start()
                }
                .start()
        }
    }

    private fun showEventList() {
        val events = randomEventManager.getEvents()
        if (events.isEmpty()) return

        val eventListDialog = android.app.AlertDialog.Builder(this)
            .setTitle("遭遇事件")
            .setItems(events.toTypedArray()) { _, which ->
                handleEvent(events[which]) // **點擊事件後處理**
            }
            .setPositiveButton("關閉") { _, _ -> }
            .show()
    }

    private fun loadItemsFromJson(fileName: String, itemType: Type, onSuccess: (List<Item>) -> Unit) {
        try {
            val jsonString = assets.open(fileName).bufferedReader().use { it.readText() }
            val items: List<Item> = Gson().fromJson(jsonString, itemType)
            onSuccess(items)
        } catch (e: Exception) {
            Log.e("MainActivity", "讀取 $fileName 失敗: ${e.message}")
        }
    }

    private fun handleEvent(event: String) {
        when (event) {
            "遭遇敵人！⚔" -> startBattle()
            "發現食物 🌿" -> collectHerb()
            "找到寶藏 💎" -> collectTreasure()
            "遇見同伴 🧙" -> talkToNPC()
        }

        randomEventManager.removeEvent(event)

        if (randomEventManager.getEvents().isEmpty()) {
            eventNotificationTextView.visibility = View.GONE
        }
    }

    private fun startBattle() {
        val selectedEnemy = enemies.random()
        val intent = Intent(this, BattleActivity::class.java)
        intent.putExtra("enemy", selectedEnemy)
        startActivity(intent)
    }


    private fun talkToNPC() {
        android.app.AlertDialog.Builder(this)
            .setTitle("遇見同伴 🧙")
            .setMessage("同伴: 你好，修行者。請繼續努力修煉！")
            .setPositiveButton("確定", null)
            .show()
    }

    private fun collectHerb() {
        if (herbs.isEmpty()) {
            Log.e("MainActivity", "靈草列表為空！")
            return
        }

        val randomHerb = herbs.random()
        backpack.addItem(randomHerb)

        android.app.AlertDialog.Builder(this)
            .setTitle("發現 ${randomHerb.name}！")
            .setMessage("你撿到了 ${randomHerb.description}，已存入背包！")
            .setPositiveButton("確定", null)
            .show()
    }

    private fun collectTreasure() {
        if (treasures.isEmpty()) {
            Log.e("MainActivity", "寶藏列表為空！")
            return
        }

        val randomTreasure = treasures.random()
        backpack.addItem(randomTreasure)

        android.app.AlertDialog.Builder(this)
            .setTitle("發現 ${randomTreasure.name}！")
            .setMessage("你找到了 ${randomTreasure.description}，已存入背包！")
            .setPositiveButton("確定", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }

        stepCounterSensor?.let {
            sensorManager.registerListener(
                stepCounterHelper,
                it,
                SensorManager.SENSOR_DELAY_UI // ✅ 讓 UI 更新更即時
            )
        }

        // 更新角色資訊
        updateCharacterInfo()

        // ✅ 恢復天氣系統運作
        if (::weatherRunnable.isInitialized) {
            weatherHandler.postDelayed(weatherRunnable, 5 * 60 * 1000)
        }

        // ✅ 恢復 Keep Screen On 設定
        if (::sharedPreferences.isInitialized) {
            if (sharedPreferences.getBoolean("keepScreenOn", true)) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // 檢查 OSM 地圖是否顯示
        val showOSM = sharedPreferences.getBoolean("showOSM", false)
        val gpsEnabled = sharedPreferences.getBoolean("gpsEnabled", false)

        if (showOSM) {
            mapView.visibility = View.VISIBLE
            staticBackground.visibility = View.GONE

            // ✅ 加入這段，等 MapView layout 完成後再設置中心與縮放
            mapView.post {
                val defaultPoint = GeoPoint(25.0330, 121.5654) // 台北 101
                mapView.controller.setCenter(defaultPoint)
                mapView.controller.setZoom(18.0)
                Log.d("OSM_TEST", "首次顯示時 setCenter & setZoom")
            }

        } else {
            mapView.visibility = View.GONE
            staticBackground.visibility = View.VISIBLE
        }

        // 檢查 GPS 設定
        if (gpsEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                getLocationAndSetMapCenter()
            } else {
                checkPermissions() // 只有當權限真的缺少時，才請求權限
            }
        } else {
            if (::locationManager.isInitialized) {
                locationManager.removeUpdates(locationListener)
                isTrackingLocation = false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) {
            mapView.onPause()   // 停止地圖更新，減少背景運行
        }

        // ✅ 依據使用者設定決定是否停止步數計算
        val backgroundStepsEnabled = sharedPreferences.getBoolean("backgroundSteps", true)
        if (!backgroundStepsEnabled && ::sensorManager.isInitialized) {
            try {
                sensorManager.unregisterListener(stepCounterHelper)
            } catch (e: Exception) {
                Log.e("StepCounter", "步數監聽器未註冊，無法取消註冊: ${e.message}")
            }
        }

        // 停止天氣
        weatherHandler.removeCallbacks(weatherRunnable)

        // **停止 GPS 監聽**
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(locationListener)
            isTrackingLocation = false
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getLocationAndSetMapCenter()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                REQUEST_ACTIVITY_RECOGNITION_PERMISSION
            )
        } else {
            startStepCounter()
        }
    }

    private fun startStepCounter() {
        stepCounterSensor?.let {
            sensorManager.registerListener(
                stepCounterHelper,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationAndSetMapCenter() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            if (::locationManager.isInitialized) {
                val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val startPoint = if (location != null) {
                    GeoPoint(location.latitude, location.longitude)
                } else {
                    GeoPoint(25.0330, 121.5654) // 預設台北 101
                }

                mapView.controller.setCenter(startPoint)
                mapView.setMultiTouchControls(true) // 啟用手勢縮放
                mapView.controller.setZoom(18.0)

                // 移除舊的標記，避免重複顯示
                mapView.overlays.clear()

                val marker = Marker(mapView)
                marker.position = startPoint
                marker.title = "你在這裡！"
                mapView.overlays.add(marker)

                // **開始監聽 GPS 變化**
                if (!isTrackingLocation) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000L, // **更新間隔 (毫秒)**
                        2f,    // **更新距離 (公尺)**
                        locationListener
                    )
                    isTrackingLocation = true
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 使用者允許 GPS 權限
                getLocationAndSetMapCenter()
            } else {
                // **使用者拒絕 GPS 權限，顯示提示**
                android.app.AlertDialog.Builder(this)
                    .setTitle("位置權限被拒絕")
                    .setMessage("此應用程式需要位置權限來追蹤你的移動，請在設定中允許位置權限。")
                    .setPositiveButton("確定") { _, _ -> }
                    .show()
            }
        }

        if (requestCode == REQUEST_ACTIVITY_RECOGNITION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startStepCounter()
            } else {
                Log.w("Permissions", "使用者拒絕了步數偵測權限")
            }
        }
    }
}
