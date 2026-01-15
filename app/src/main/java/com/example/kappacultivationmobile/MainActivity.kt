package com.example.kappacultivationmobile

import com.example.kappacultivationmobile.databinding.ActivityMainBinding
import android.Manifest
import android.util.Log
import android.text.Html
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import org.osmdroid.views.overlay.Marker
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import com.example.kappacultivationmobile.models.Enemy
import com.example.kappacultivationmobile.battle.BattleActivity
import com.example.kappacultivationmobile.models.LevelMilestone


inline fun <reified T> typeToken() = object : TypeToken<T>() {}

enum class WeatherType {
    SUNNY, RAINY, SNOWY, NORNAML
}

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    //  系統服務變數
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: android.location.LocationListener
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null //  步數管理

    //  邏輯管理變數
    private lateinit var stepCounterHelper: StepCounterHelper
    private val levelManager = LevelManager()   // 等級管理
    private lateinit var achievementManager: AchievementManager //  成就管理
    private lateinit var backpack: Backpack // 背包管理

    // 狀態紀錄與角色回應變數
    private lateinit var characterResponse: CharacterResponse
    private lateinit var sharedPreferences: SharedPreferences

    // 天氣邏輯變數
    private var currentWeather: WeatherType? = null
    private lateinit var rainEffectManager: RainEffectManager // 天氣管理 (下雨)
    private lateinit var snowEffectManager: SnowEffectManager // 天氣管理 (下雪)
    private val weatherHandler = android.os.Handler(Looper.getMainLooper())
    private lateinit var weatherRunnable: Runnable

    // 數據狀態變數
    private var enemies: MutableList<Enemy> = mutableListOf()
    private var isNavigatingToOtherActivity = false // 是否正在導航到其他 Activity
    private var isTrackingLocation = false
    private lateinit var currentPlayerStats: LevelMilestone

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
    private val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1
    private val REQUEST_LOCATION_PERMISSION = 2

    // 互動功能
    private lateinit var petStatus: PetStatus
    private lateinit var petActions: PetActions
    private lateinit var petUpdateManager: PetUpdateManager
    private lateinit var randomEventManager: RandomEventManager

    // 遭遇
    private lateinit var herbs: List<Item>
    private lateinit var treasures: List<Item>


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "com.example.kappacultivationmobile/1.0 (KappaApp)"

        super.onCreate(savedInstanceState)

        // 初始化 ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 背景音樂
        val selectedBGM = sharedPreferences.getInt("mainBgmSelection", 0)
        val bgmResId = when (selectedBGM) {
            0 -> R.raw.bgm_1
            1 -> R.raw.bgm_2
            2 -> R.raw.bgm_3
            else -> null
        }

        // 預設是否保持螢幕常亮
        if (sharedPreferences.getBoolean("keepScreenOn", true)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // 初始化 LocationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val gpsEnabled = sharedPreferences.getBoolean("gpsEnabled", false)
        val showOSM = sharedPreferences.getBoolean("showOSM", false)

        // 設定 OpenStreetMap 配置
        Configuration.getInstance().userAgentValue = packageName
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)

        if (showOSM) {
            binding.mapView.visibility = View.VISIBLE
            binding.staticBackground.visibility = View.GONE
        } else {
            binding.mapView.visibility = View.GONE
            binding.staticBackground.visibility = View.VISIBLE
        }
        binding.staticBackground.setImageResource(R.drawable.background_day)    // 預設背景

        // 天氣系統
        rainEffectManager = RainEffectManager(this, binding.weatherLayer)
        snowEffectManager = SnowEffectManager(this, binding.weatherLayer)

        // 啟動天氣輪替（立刻執行一次 + 每 1 分鐘切換）
        binding.mainButtonLayout.post {
            changeWeather() // 第一次天氣設定
            weatherRunnable = object : Runnable {
                override fun run() {
                    changeWeather()
                    weatherHandler.postDelayed(this, 5 * 60 * 1000)
                }
            }
            weatherHandler.postDelayed(weatherRunnable, 5 * 60 * 1000)
        }

        //  互動按鈕群組
        val interactButtons = listOf(binding.buttonFeed, binding.buttonPlay, binding.buttonClean)
        val exploreButtons = listOf(binding.buttonExploreOut, binding.buttonExploreChallenge)

        // 初始隱藏
        interactButtons.forEach { it.visibility = View.GONE }
        exploreButtons.forEach { it.visibility = View.GONE }

        // 探險主按鈕點擊事件：展開/收合
        binding.buttonExplore.setOnClickListener {
            val newVisibility = if (binding.buttonExploreOut.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            interactButtons.forEach { it.visibility = View.GONE }
            exploreButtons.forEach { it.visibility = newVisibility }

            if (newVisibility == View.VISIBLE) {
                binding.exploreGroup.bringToFront() // 使用 binding.exploreGroup
            }
        }

        // 互動主按鈕點擊事件：展開/收合
        binding.buttonInteract.setOnClickListener {
            val newVisibility = if (binding.buttonFeed.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            exploreButtons.forEach { it.visibility = View.GONE }
            interactButtons.forEach { it.visibility = newVisibility }

            if (newVisibility == View.VISIBLE) {
                binding.interactGroup.bringToFront() // 使用 binding.interactGroup
            }
        }

        // 成就主按鈕點擊事件：展開/收合
        binding.buttonAchievements.setOnClickListener {
            isNavigatingToOtherActivity = true
            startActivity(Intent(this, AchievementsActivity::class.java))
        }

        binding.buttonBackpack.setOnClickListener {
            isNavigatingToOtherActivity = true
            startActivity(Intent(this, BackpackTabbedActivity::class.java))
        }

        binding.buttonSettings.setOnClickListener {
            isNavigatingToOtherActivity = true
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 角色定時動畫
        animationHandler.postDelayed(animationRunnable, 3000)

        // 處理角色觸碰旋轉事件
        binding.characterImage.setOnTouchListener { view, event ->
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
                    if (Math.abs(event.x - touchStartX) <= 30) {
                        view.performClick()
                    }
                    true
                }

                else -> false
            }
        }

        // 處理角色點擊事件
        binding.characterImage.setOnClickListener {
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
                    "我可是第一可愛！",
                    "快給我點好吃的嘛！",
                    "再點我一次試試看？",
                    "嘻嘻～～",
                    "有什麼寶藏要給我嗎？",
                    "我想睡覺了啦..."
                ).random()
            }
            binding.characterResponse.text = message
            binding.characterResponse.visibility = View.VISIBLE
            binding.characterResponse.postDelayed({
                binding.characterResponse.visibility = View.GONE
            }, 4000)
        }

        // 初始化 locationListener
        // 初始化 locationListener
        locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                val newGeoPoint = GeoPoint(location.latitude, location.longitude)
                // 使用 binding.mapView
                binding.mapView.controller.setCenter(newGeoPoint)
                binding.mapView.overlays.clear()
                val marker = Marker(binding.mapView)
                marker.position = newGeoPoint
                marker.title = "你在這裡！"
                binding.mapView.overlays.add(marker)
                Log.d("GPS Update", "位置更新: ${location.latitude}, ${location.longitude}")
            }
            // 從 JSON 檔案中讀取背包的物品資料，並將讀取到的資料儲存到 Backpack 類別的 items 映射中
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 讀取數據與初始化 LevelManager
        val savedSteps = sharedPreferences.getInt("currentStepsInLevel", 0)
        val savedLevel = sharedPreferences.getInt("currentLevel", 1)
        val savedExp = sharedPreferences.getLong("currentExp", 0L)
        levelManager.loadData(savedLevel, savedExp)

        // 使用 binding.tvStatus
        binding.tvStatus.text = getString(R.string.level_and_steps, savedLevel, savedSteps)

        // 初始化角色回應
        characterResponse = CharacterResponse()

        // ✅ 電子雞系統初始化
        petStatus = PetStatus()
        petActions = PetActions(petStatus)
        petUpdateManager = PetUpdateManager(petStatus) { updateUI() } // updateUI 會用到 binding

        // 初始化 StepCounterHelper
        stepCounterHelper = StepCounterHelper(
            { steps, level, response ->
                runOnUiThread {
                    binding.tvStatus.text = getString(R.string.level_and_steps, level, steps)
                    binding.characterResponse.removeCallbacks(null)

                    if (response.isNotEmpty()) {
                        binding.characterResponse.text = response
                        binding.characterResponse.visibility = View.VISIBLE
                        binding.characterResponse.postDelayed({
                            if (binding.characterResponse.text == response) {
                                binding.characterResponse.visibility = View.GONE
                            }
                        }, 6000)
                    } else {
                        binding.characterResponse.visibility = View.GONE
                    }
                    updateCharacterInfo()
                }
            },
            levelManager,
            sharedPreferences,
            characterResponse,
            30,
            petStatus
        )

        // 初始化成就管理
        achievementManager = AchievementManager(this)


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

        // ✅ 初始化音效管理器
        EffectSoundManager.init(applicationContext)


        // 初始化 SensorManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        //商城頁面入口圖示邏輯
        binding.marketIcon.setOnClickListener {
            isNavigatingToOtherActivity = true
            startActivity(Intent(this, MarketActivity::class.java))
        }

        // 穿戴頁面入口圖示邏輯
        binding.equipmentIcon.setOnClickListener {
            Toast.makeText(this, "裝備穿戴頁面 - 開發中...", Toast.LENGTH_SHORT).show()
        }

        // 餵食按鈕邏輯
        binding.buttonFeed.setOnClickListener {
            // 1. 執行寵物基礎動作 (飢餓度等)
            petActions.feed()

            // 2. 回血處理邏輯 (使用 currentPlayerStats)
            val maxHp = currentPlayerStats.health
            val oldHp = sharedPreferences.getInt("currentHp", maxHp)

            // 計算回復 20%
            val restoredAmount = (maxHp * 0.2).toInt()
            val updatedHp = (oldHp + restoredAmount).coerceAtMost(maxHp)

            // 3. 儲存數值與成就統計
            val feedTimes = sharedPreferences.getInt("feed_times", 0) + 1

            sharedPreferences.edit()
                .putInt("currentHp", updatedHp)
                .putInt("feed_times", feedTimes)
                .apply()

            // 4. 更新畫面
            updateCharacterInfo() // 刷新 HP 文字顯示
            updateUI()            // 刷新圖片狀態

            Toast.makeText(this, "餵食成功！回復了 $restoredAmount HP", Toast.LENGTH_SHORT).show()
        }

        // 娛樂按鈕邏輯
        binding.buttonPlay.setOnClickListener {
            petActions.play()
            updateUI()

            // ➕ 記錄娛樂次數（供成就系統用）
            val playTimes = sharedPreferences.getInt("play_times", 0) + 1
            sharedPreferences.edit().putInt("play_times", playTimes).apply()
        }

        // 清潔按鈕邏輯
        binding.buttonClean.setOnClickListener {
            petActions.clean()
            updateUI()

            val cleanTimes = sharedPreferences.getInt("clean_times", 0) + 1
            sharedPreferences.edit().putInt("clean_times", cleanTimes).apply()
        }

        // 外出按鈕邏輯
        binding.buttonExploreOut.setOnClickListener {
            // 💡 外出功能：可設定為啟動或停止 GPS/步數追蹤，或清除地圖標記等
            Toast.makeText(this, "你開始外出探險了！", Toast.LENGTH_SHORT).show()

            // TODO: 新增外出時間/距離計算
        }

        // 挑戰按鈕邏輯
        binding.buttonExploreChallenge.setOnClickListener {
            // 💡 挑戰功能：可設定為立即觸發一次隨機事件，或進入一個戰鬥列表介面
            Toast.makeText(this, "你決定挑戰強敵！", Toast.LENGTH_SHORT).show()

            // 假設我們要立即觸發一個隨機戰鬥 (類似現有的 random event)
            startBattle() // 重用現有的戰鬥啟動函數
        }

        // ✅ 開始狀態變化（每 60 秒執行一次）
        petUpdateManager.startUpdating()

        // 讀取遭遇物品
        loadItemsFromJson("herbs.json", object : TypeToken<List<Item>>() {}.type) { items: List<Item> -> // 👈 顯式指定類型
            herbs = items
            Log.d("MainActivity", "讀取到 ${herbs.size} 種靈草")
        }
        loadItemsFromJson("treasures.json", object : TypeToken<List<Item>>() {}.type) { items: List<Item> -> // 👈 顯式指定類型
            treasures = items
            Log.d("MainActivity", "讀取到 ${treasures.size} 種寶藏")
        }

        // 隨機事件
        randomEventManager = RandomEventManager { event ->
            runOnUiThread {
                binding.tvEventNotification.text = event
                binding.tvEventNotification.visibility = View.VISIBLE
            }
        }
        binding.tvEventNotification.setOnClickListener {
            showEventList()
        }

        randomEventManager.startEventLoop() // 啟動隨機事件

        // **初始化背包**
        backpack = Backpack(this)

        // 檢查裝置是否支援 TYPE_STEP_COUNTER
        val hasStepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        Log.d("SensorCheck", "裝置是否支援 TYPE_STEP_COUNTER：$hasStepSensor")

        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        Log.d("PermissionCheck", "目前是否有 ACTIVITY_RECOGNITION 權限：$hasPermission")

        val backgroundStepsEnabled = sharedPreferences.getBoolean("backgroundSteps", true)
        Log.d("StepConfig", "backgroundSteps 設定為：$backgroundStepsEnabled")


        // 檢查權限
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToOtherActivity = false

        binding.mapView.onResume()

        // ✅ 根據使用者是否允許背景步數計算來判斷是否啟用感應器
        val backgroundStepsEnabled = sharedPreferences.getBoolean("backgroundSteps", true)
        Log.d("StepResume", "onResume() 呼叫，backgroundSteps=$backgroundStepsEnabled")

        if (backgroundStepsEnabled) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("StepResume", "✅ 權限已授權，準備註冊感應器")
                stepCounterSensor?.let {
                    sensorManager.registerListener(
                        stepCounterHelper,
                        it,
                        SensorManager.SENSOR_DELAY_UI
                    )
                    Log.d("StepResume", "✅ 已註冊步數感應器 listener")
                } ?: Log.e("StepResume", "❌ stepCounterSensor 為 null，無法註冊")
            } else {
                Log.w("StepResume", "❌ 沒有 ACTIVITY_RECOGNITION 權限")
            }
        } else {
            Log.d("StepResume", "❌ 背景步數計算設定為 false，不註冊感應器")
        }

        // 更新角色資訊
        updateCharacterInfo()

        // ✅ 背景音樂播放
        val selectedBGM = sharedPreferences.getInt("mainBgmSelection", 0)
        val bgmResId = when (selectedBGM) {
            0 -> R.raw.bgm_1
            1 -> R.raw.bgm_2
            2 -> R.raw.bgm_3
            else -> R.raw.bgm_1
        }
        BgmManager.play(this, bgmResId, "Main")

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
            binding.mapView.visibility = View.VISIBLE
            binding.staticBackground.visibility = View.GONE

            // ✅ 使用 binding.mapView
            binding.mapView.post {
                val defaultPoint = GeoPoint(25.0330, 121.5654)
                binding.mapView.controller.setCenter(defaultPoint)
                binding.mapView.controller.setZoom(18.0)
            }
        } else {
            binding.mapView.visibility = View.GONE
            binding.staticBackground.visibility = View.VISIBLE
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
        binding.mapView.onPause()

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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isNavigatingToOtherActivity) {
            BgmManager.pause()
            Log.d("BGM", "App 退到背景，暫停音樂")
        } else {
            Log.d("BGM", "切換至其他功能頁，不暫停音樂")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 1. 移除所有計時器，防止記憶體洩漏
        weatherHandler.removeCallbacksAndMessages(null)
        animationHandler.removeCallbacksAndMessages(null)
        binding.characterResponse.removeCallbacks(null)

        // 2. 停止音樂
        BgmManager.stop()

        // 3. 強制取消註冊步數感應器
        if (::sensorManager.isInitialized && stepCounterSensor != null) {
            try {
                sensorManager.unregisterListener(stepCounterHelper)
                Log.d("StepDestroy", "Activity 銷毀，強制移除步數監聽器")
            } catch (e: Exception) {
                Log.e("StepDestroy", "移除監聽器失敗: ${e.message}")
            }
        }
    }

    // 遭遇戰鬥
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
        // 1. 停止舊天氣
        rainEffectManager.stopRain()
        snowEffectManager.stopSnow()

        // 2. 隨機切換
        when ((1..4).random()) {
            1 -> { // 晴天
                currentWeather = WeatherType.SUNNY
                binding.staticBackground.setImageResource(R.drawable.background_sunny)
            }
            2 -> { // 雨天
                currentWeather = WeatherType.RAINY
                binding.staticBackground.setImageResource(R.drawable.background_rainy)

                // ✅ 使用 binding.mainButtonLayout 取得位置
                val targetY = binding.mainButtonLayout.top.toFloat() - 70f

                rainEffectManager.rainTargetY = targetY
                rainEffectManager.splashY = targetY
                rainEffectManager.startRain(dropCount = 40, angle = 10f)
            }
            3 -> { // 下雪
                currentWeather = WeatherType.SNOWY
                binding.staticBackground.setImageResource(R.drawable.background_snowy)

                val targetY = binding.mainButtonLayout.top.toFloat() - 70f
                snowEffectManager.snowTargetY = targetY
                snowEffectManager.startSnow()
            }
            4 -> { // 一般
                currentWeather = WeatherType.NORNAML
                binding.staticBackground.setImageResource(R.drawable.background_day)
            }
        }
    }

    private fun updateCharacterInfo() {
        val savedLevel = sharedPreferences.getInt("currentLevel", 1)

        // 確保 LevelManager 狀態同步 (防止在其他地方修改了 Prefs 但 Manager 沒更新)
        if (levelManager.currentLevel != savedLevel) {
            val savedExp = sharedPreferences.getLong("currentExp", 0L)
            levelManager.loadData(savedLevel, savedExp)
        }

        // 使用 LevelManager 取得數值(LevelManager.kt)
        val stats = levelManager.getStatsForLevel(savedLevel)
        currentPlayerStats = stats
        val currentGold = sharedPreferences.getInt("player_gold", 0)
        val currentHp = sharedPreferences.getInt("currentHp", stats.health)

        // 設定 HP 文字顏色 (危險紅/警告橘)
        val coloredHp = when {
            currentHp < stats.health * 0.4 -> "<font color='#FF4444'>$currentHp</font>"
            currentHp < stats.health * 0.7 -> "<font color='#FFBB33'>$currentHp</font>"
            else -> currentHp.toString()
        }

        val characterInfoText = """
            等級: ${stats.level}<br>
            HP: $coloredHp / ${stats.health}<br>
            魔力: ${stats.mana}<br>
            攻擊: ${stats.attack}<br>
            防禦: ${stats.defense}<br>
            金幣: $currentGold
        """.trimIndent()

        binding.characterInfo.setText(
            Html.fromHtml(characterInfoText, Html.FROM_HTML_MODE_LEGACY),
            TextView.BufferType.SPANNABLE
        )
    }

    private fun updateUI() {
        binding.tvPetStatus.text = getString(
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
        // 正常狀態圖片組
        val normalVariants = listOf(
            R.drawable.emoji_normal_1,
            R.drawable.emoji_normal_2,
            R.drawable.emoji_normal_3
        )

        // 損血時
        val currentHp = sharedPreferences.getInt("currentHp", currentPlayerStats.health)

        // ✅ 血量低於 70%，優先顯示「受傷」圖片
        if (currentHp < currentPlayerStats.health * 0.7) {
            binding.characterImage.setImageResource(R.drawable.emoji_injured)
            return
        }

        // 狀態變更時
        var characterImageKey = "cool"
        when {
            petStatus.cleanliness < 70 -> characterImageKey = "sick"
            petStatus.hunger < 70 -> characterImageKey = "hungry"
            petStatus.energy < 75 -> characterImageKey = "tired"
            petStatus.mood < 60 -> characterImageKey = "mood"

            // 如果狀態都很平均且不錯，隨機顯示正常圖片
            (petStatus.hunger + petStatus.energy + petStatus.cleanliness + petStatus.mood) / 4 >= 90 -> {
                val normalVariants = listOf(R.drawable.emoji_normal_1, R.drawable.emoji_normal_2, R.drawable.emoji_normal_3)
                binding.characterImage.setImageResource(normalVariants.random())
                return
            }
        }
        binding.characterImage.setImageResource(characterImages[characterImageKey] ?: R.drawable.emoji_happy)

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

            binding.characterImage.animate()
                .translationYBy(-jumpHeight)
                .setDuration(animationDuration / 2)
                .setInterpolator(AccelerateDecelerateInterpolator()) // 加速減速
                .withEndAction {
                    binding.characterImage.animate()
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
            binding.characterImage.animate()
                .rotationBy(randomRotation)
                .setDuration(animationDuration)
                .withEndAction {
                    binding.characterImage.animate()
                        .rotationBy(-randomRotation)
                        .setDuration(animationDuration)
                        .withEndAction {
                            binding.characterImage.animate()
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
            binding.tvEventNotification.visibility = View.GONE
        }
    }

    private fun startBattle() {
        val selectedEnemy = EnemyManager.getRandomEnemy() ?: return
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
                binding.mapView.controller.setCenter(startPoint)
                binding.mapView.setMultiTouchControls(true) // 啟用手勢縮放
                binding.mapView.controller.setZoom(18.0)

                // 移除舊的標記，避免重複顯示
                binding.mapView.overlays.clear()

                val marker = Marker(binding.mapView)
                marker.position = startPoint
                marker.title = "你在這裡！"
                binding.mapView.overlays.add(marker)

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
            Log.d("PermissionCheck", "onRequestPermissionsResult：步數權限回傳 result=${grantResults.joinToString()}")
        }

        if (requestCode == REQUEST_ACTIVITY_RECOGNITION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 這裡不需要呼叫 startStepCounter()，因為系統關閉權限視窗後會觸發 onResume
                Log.d("PermissionCheck", "使用者剛授權步數權限，等待 onResume 啟動感應器")
            } else {
                Log.w("Permissions", "使用者拒絕了步數偵測權限")
                // 這裡可以考慮跳出一個 Dialog 告訴使用者：沒有權限就不能孵蛋喔
            }
        }
    }
}
