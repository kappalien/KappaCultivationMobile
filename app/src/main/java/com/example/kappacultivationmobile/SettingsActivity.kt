package com.example.kappacultivationmobile

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchBackgroundSteps: Switch
    private lateinit var switchGPS: Switch
    private lateinit var switchShowOSM: Switch


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 綁定 UI 元件
        switchBackgroundSteps = findViewById(R.id.switchBackgroundSteps)
        switchGPS = findViewById(R.id.switchGPS)
        switchShowOSM = findViewById(R.id.switchShowOSM)

        // 讀取儲存的設定值
        switchBackgroundSteps.isChecked = sharedPreferences.getBoolean("backgroundSteps", true)
        switchGPS.isChecked = sharedPreferences.getBoolean("gpsEnabled", false)
        switchShowOSM.isChecked = sharedPreferences.getBoolean("showOSM", false)

        // 當使用者改變開關或滑桿時，馬上更新設定值
        switchBackgroundSteps.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("backgroundSteps", isChecked).apply()
        }

        switchGPS.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("gpsEnabled", isChecked).apply()
        }

        switchShowOSM.setOnCheckedChangeListener { _, isChecked -> // ➜ 新增 OSM 設定
            sharedPreferences.edit().putBoolean("showOSM", isChecked).apply()
        }
    }
}