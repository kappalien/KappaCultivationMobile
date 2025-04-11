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
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import android.widget.LinearLayout
import android.util.Log
import android.view.MotionEvent
import java.lang.reflect.Type
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.view.ViewGroup.LayoutParams
import java.util.Calendar



inline fun <reified T> typeToken() = object : TypeToken<T>() {}

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var locationListener: android.location.LocationListener
    private var isTrackingLocation = false

    private var stepCounterSensor: Sensor? = null
    private lateinit var stepCounterHelper: StepCounterHelper

    // 依時間改變背景圖片
    data class TimeBackground(val startTime: Int, val endTime: Int, val drawableId: Int)
    private val timeBackgrounds = listOf(
        TimeBackground(0, 6, R.drawable.background_night),
        TimeBackground(6, 9, R.drawable.background_dawn),
        TimeBackground(9, 17, R.drawable.background_day),
        TimeBackground(17, 20, R.drawable.background_dusk),
        TimeBackground(20, 24, R.drawable.background_night)
    )

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

    private lateinit var levelInfoList: List<LevelInfo>

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
    private lateinit var rvBackpack: RecyclerView // 背包物品列表
    private lateinit var backpackContainer: LinearLayout // 背包 UI 容器
    private lateinit var btnCloseBackpack: Button // 關閉背包按鈕

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 初始化 LocationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val gpsEnabled = sharedPreferences.getBoolean("gpsEnabled", false)
        val showOSM = sharedPreferences.getBoolean("showOSM", false)

        // 設定 OpenStreetMap 配置
        Configuration.getInstance().userAgentValue = packageName

        // 設定 Activity 的 Layout
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        staticBackground = findViewById<ImageView>(R.id.staticBackground)

        // 檢查是否要顯示 OSM 地圖
        if (showOSM) {
            mapView.visibility = View.VISIBLE
            staticBackground.visibility = View.GONE
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
        } else {
            mapView.visibility = View.GONE
            staticBackground.visibility = View.VISIBLE
        }

        // 背景效果(依時間改變)
        updateBackgroundForTime()

        val timeHandler = android.os.Handler(Looper.getMainLooper())
        val timeRunnable = object : Runnable {
            override fun run() {
                updateBackgroundForTime()
                timeHandler.postDelayed(this, 60000)
            }
        }
        timeHandler.postDelayed(timeRunnable, 60000)

        // 設定按鈕
        val settingsButton: Button = findViewById(R.id.buttonSettings)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 開始角色定時動畫
        animationHandler.postDelayed(animationRunnable, 5000)

        // 初始化 UI 元件
        mapView = findViewById(R.id.mapView)
        tvStatus = findViewById(R.id.tv_status) // 等級資訊
        characterImage = findViewById(R.id.character_image) // 角色圖片


        // 角色旋轉
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
//        characterImage.setOnClickListener {
//            Toast.makeText(this, "角色被點擊了", Toast.LENGTH_SHORT).show()
//        }

        characterStatusIcon = findViewById(R.id.character_status_icon) // 狀態圖示
        characterInfo = findViewById(R.id.character_info)   // 角色資訊
        characterResponseTextView = findViewById(R.id.character_response)   // 角色回應

        // 初始化 UI
        rvBackpack = findViewById(R.id.rvBackpack)
        backpackContainer = findViewById(R.id.backpackContainer)
        btnCloseBackpack = findViewById(R.id.btnCloseBackpack)

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

        // 確保 JSON 正常讀取
        if (levelInfoList.isEmpty()) {
            Log.e("CharacterInfo", "levelInfoList 為空，可能 JSON 讀取失敗！")
        }

        // 初始化角色回應
        characterResponse = CharacterResponse()

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
            characterResponse
        )


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

        // 設定地圖
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // ✅ 電子雞系統初始化
        petStatus = PetStatus()
        petActions = PetActions(petStatus)
        petUpdateManager = PetUpdateManager(petStatus) { updateUI() }

        // ✅ 綁定 UI 按鈕
        findViewById<Button>(R.id.button_feed).setOnClickListener { petActions.feed(); updateUI() }
        findViewById<Button>(R.id.button_meditate).setOnClickListener { petActions.meditate(); updateUI() }
        findViewById<Button>(R.id.button_play).setOnClickListener { petActions.play(); updateUI() }
        findViewById<Button>(R.id.button_clean).setOnClickListener { petActions.clean(); updateUI() }

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

        // 設置 RecyclerView (Grid 格式，每行 3 個)
        rvBackpack.layoutManager = GridLayoutManager(this, 3)

        // 預設隱藏背包
        backpackContainer.visibility = View.GONE

        // 設定 "打開背包" 按鈕
        findViewById<Button>(R.id.button_backpack).setOnClickListener {
            showBackpack()
        }

        // 設定 "關閉背包" 按鈕
        btnCloseBackpack.setOnClickListener {
            backpackContainer.visibility = View.GONE
        }

        findViewById<Button>(R.id.button_backpack).setOnClickListener {
            showBackpack()
        }

        // 檢查權限
        checkPermissions()
    }

    private fun updateBackgroundForTime() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val background = timeBackgrounds.find { currentHour >= it.startTime && currentHour < it.endTime }
        if (background != null) {
            staticBackground.setImageResource(background.drawableId)
        } else {
            staticBackground.setImageResource(R.drawable.background_image)
        }
    }

    private fun updateCharacterInfo() {
        val savedLevel = sharedPreferences.getInt("currentLevel", 1)
        if (savedLevel in 1..levelInfoList.size) {
            val levelInfo = levelInfoList[savedLevel - 1]
            val currentGold = sharedPreferences.getInt("player_gold", 0)
            characterInfo.text = getString(
                R.string.character_info, levelInfo.level, levelInfo.health,
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

        var characterImageKey = "cool" // 預設圖片

        if (petStatus.cleanliness < 70) {
            characterImageKey = "sick"
        } else if (petStatus.hunger < 70) {
            characterImageKey = "hungry"
        } else if (petStatus.energy < 75) {
            characterImageKey = "tired"
        } else if (petStatus.mood < 60) {
            characterImageKey = "mood"
        } else if (petStatus.hunger < 85 || petStatus.energy < 85 || petStatus.cleanliness < 85 || petStatus.mood < 85) {
            characterImageKey = "normal"
        }
        characterImage.setImageResource(characterImages[characterImageKey] ?: R.drawable.emoji_happy)

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
//            "遭遇敵人！⚔" -> startBattle()
//            "遇見修仙 NPC 🧙" -> talkToNPC()
            "發現靈草 🌿" -> collectHerb()
            "找到寶藏 💎" -> collectTreasure()

        }

        randomEventManager.removeEvent(event)

        if (randomEventManager.getEvents().isEmpty()) {
            eventNotificationTextView.visibility = View.GONE
        }
    }

    private fun startBattle() {
        android.app.AlertDialog.Builder(this)
            .setTitle("戰鬥開始！")
            .setMessage("你遇到了一名敵人！是否進行戰鬥？")
            .setPositiveButton("戰鬥") { _, _ ->
                // 這裡可以加入戰鬥邏輯
            }
            .setNegativeButton("逃跑", null)
            .show()
    }

    private fun talkToNPC() {
        android.app.AlertDialog.Builder(this)
            .setTitle("遇見修仙 NPC")
            .setMessage("NPC: 你好，修行者。請繼續努力修煉！")
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

    private fun showBackpack() {
        val items = backpack.getItems()

        if (items.isEmpty()) {
            Toast.makeText(this, "你的背包是空的！", Toast.LENGTH_SHORT).show()
        } else {
            rvBackpack.adapter = BackpackAdapter(items.toMutableList(), ::onItemClicked)
            backpackContainer.visibility = View.VISIBLE
        }
    }

    // 點擊物品時的處理
    private fun onItemClicked(item: Item) {
        val options = mutableListOf<String>()
        if (item.effects.isNotEmpty()) options.add("使用")
        if (item.sellable) options.add("出售")

        if (options.isEmpty()) {
            showToast("這個物品無法使用或出售")
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("選擇操作 - ${item.name}")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "使用" -> useItem(item.itemId)
                    "出售" -> sellItem(item.itemId)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // **使用物品**
    private fun useItem(itemId: String) {
        val item = backpack.getItems().find { it.itemId == itemId }

        if (item != null) {
            // ✅ 僅顯示使用訊息
            showToast("你使用了 ${item.name}")

            // ✅ 扣除物品數量，並刷新背包畫面
            backpack.removeItem(itemId, 1) {
                showBackpack()
            }
        }
    }

    // **賣出物品**
    private fun sellItem(itemId: String) {
        val item = backpack.getItems().find { it.itemId == itemId }

        if (item != null) {
            if (!item.sellable) {
                Log.w("Backpack", "無法出售 ${item.name}，該物品不可販賣！")
                return
            }

            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setTitle("出售 ${item.name}")

            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_NUMBER
            input.hint = "輸入出售數量 (最多 ${item.quantity})"

            val layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            input.layoutParams = layoutParams

            val container = LinearLayout(this)
            container.orientation = LinearLayout.VERTICAL
            container.addView(input)

            dialogBuilder.setView(container)

            dialogBuilder.setPositiveButton("出售") { dialog, _ ->
                val sellAmount = input.text.toString().toIntOrNull() ?: 1 // 預設值為 1
                if (sellAmount > 0 && sellAmount <= item.quantity) {
                    // ✅ 計算金幣與更新
                    val goldEarned = item.value * sellAmount
                    val currentGold = sharedPreferences.getInt("player_gold", 0)
                    val newGold = currentGold + goldEarned
                    sharedPreferences.edit().putInt("player_gold", newGold).apply()

                    // ✅ 移除物品並刷新 UI
                    backpack.removeItem(itemId, sellAmount) {
                        showToast("售出 ${item.name} x$sellAmount 獲得 $goldEarned 金幣！")
                        showBackpack()
                        updateCharacterInfo()
                    }
                } else {
                    showToast("請輸入有效的出售數量！")
                }
                dialog.dismiss()
            }

            dialogBuilder.setNegativeButton("取消") { dialog, _ ->
                dialog.cancel()
            }

            dialogBuilder.show()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

        // 檢查OSM地圖是否顯示
        // **重新讀取最新的 GPS & OSM 設定**
        val showOSM = sharedPreferences.getBoolean("showOSM", false)
        val gpsEnabled = sharedPreferences.getBoolean("gpsEnabled", false)

        // **更新 OSM 顯示狀態**
        mapView.visibility = if (showOSM) View.VISIBLE else View.GONE

        // **檢查 GPS 設定**
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
                mapView.setMultiTouchControls(false) // 啟用手勢縮放
                mapView.controller.setZoom(20.0)

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
