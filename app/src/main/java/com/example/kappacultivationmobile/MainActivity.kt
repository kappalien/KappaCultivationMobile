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

inline fun <reified T> typeToken() = object : TypeToken<T>() {}

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var locationListener: android.location.LocationListener
    private var isTrackingLocation = false

    private var stepCounterSensor: Sensor? = null
    private lateinit var stepCounterHelper: StepCounterHelper

    private lateinit var tvStatus: TextView
    private lateinit var characterImage: ImageView
    private lateinit var characterInfo: TextView
    private lateinit var characterResponseTextView: TextView
    private lateinit var characterResponse: CharacterResponse
    private lateinit var characterInfoButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    private val KEY_STEPS = "currentStepsInLevel"
    private val KEY_LEVEL = "currentLevel"

    private val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1
    private val REQUEST_LOCATION_PERMISSION = 2

    private var isCharacterInfoVisible = false // 控制角色資訊的顯示狀態
    private lateinit var levelInfoList: List<LevelInfo>
    private var savedLevel = 1

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val gpsEnabled = sharedPreferences.getBoolean("gpsEnabled", true)
        val dialogStepInterval = sharedPreferences.getInt("dialogStepInterval", 100)

        // 設定 OpenStreetMap 配置
        Configuration.getInstance().userAgentValue = packageName

        // 設定 Activity 的 Layout
        setContentView(R.layout.activity_main)

        // 設定按鈕
        val settingsButton: Button = findViewById(R.id.buttonSettings)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 初始化 UI 元件
        mapView = findViewById(R.id.mapView)
        tvStatus = findViewById(R.id.tv_status)
        characterImage = findViewById(R.id.character_image)
        characterInfo = findViewById(R.id.character_info)
        characterResponseTextView = findViewById(R.id.character_response)
        characterInfoButton = findViewById(R.id.button_1) // 角色資訊按鈕

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

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 預設隱藏角色資訊
        characterInfo.visibility = View.GONE

        // 讀取 SharedPreferences 存儲的數據
        val savedSteps = sharedPreferences.getInt(KEY_STEPS, 0) // 🔹 讀取已儲存的步數
        savedLevel = sharedPreferences.getInt(KEY_LEVEL, 1) // 讀取等級

        tvStatus.text = "等級: $savedLevel  |  累積步數: $savedSteps" // 讀取最後一次的累加數值

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
                tvStatus.text = "等級: $level  |  累積步數: $steps"

                // 顯示角色對話
                characterResponseTextView.text = response
                characterResponseTextView.visibility = View.VISIBLE

                // 3 秒後自動隱藏對話框
                characterResponseTextView.postDelayed({
                    characterResponseTextView.visibility = View.GONE
                }, 3000)
            },
            levelInfoList,
            sharedPreferences,
            characterResponse,
            dialogStepInterval
        )

        // 是否開啟 GPS 定位
        if (!gpsEnabled) {
            locationManager.removeUpdates(locationListener)
            isTrackingLocation = false
        }

        // 設定角色資訊按鈕的點擊事件
        characterInfoButton.setOnClickListener {
            isCharacterInfoVisible = !isCharacterInfoVisible
            characterInfo.visibility = if (isCharacterInfoVisible) View.VISIBLE else View.GONE

            // 點擊按鈕時才更新角色資訊
            if (isCharacterInfoVisible) {
                if (savedLevel in 1..levelInfoList.size) {
                    val levelInfo = levelInfoList[savedLevel - 1]
                    characterInfo.text = getString(
                        R.string.character_info, levelInfo.level, levelInfo.health,
                        levelInfo.mana, levelInfo.attack, levelInfo.defense
                    )
                    Log.d("CharacterInfo", "角色資訊按鈕點擊後更新: ${characterInfo.text}")
                } else {
                    Log.e("CharacterInfo", "無法取得等級資訊，level: $savedLevel 超出範圍")
                }
            }
        }

        // 初始化 SensorManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // 設定地圖
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // 初始化 LocationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 檢查權限
        checkPermissions()
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
                //SensorManager.SENSOR_DELAY_NORMAL
                SensorManager.SENSOR_DELAY_UI // ✅ 讓 UI 更新更即時
            )
        }
        // ✅ 每次回到 App 時檢查 GPS 設定
        val gpsEnabled = sharedPreferences.getBoolean("gpsEnabled", true)
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
            mapView.onPause()
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
