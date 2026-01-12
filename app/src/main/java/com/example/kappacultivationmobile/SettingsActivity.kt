package com.example.kappacultivationmobile

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import android.widget.Spinner
import android.widget.AdapterView

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchBackgroundSteps: Switch
    private lateinit var switchGPS: Switch
    private lateinit var switchShowOSM: Switch
    private lateinit var switchKeepScreenOn: Switch


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 綁定 Spinner
        val spinnerMainBGM: Spinner = findViewById(R.id.spinnerMainBGM)

        // 讀取 SharedPreferences 中的目前選擇
        val savedIndex = sharedPreferences.getInt("mainBgmSelection", 0)
        spinnerMainBGM.setSelection(savedIndex)

        var isFirstSelection = true

        // 設定變更事件監聽器
        spinnerMainBGM.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (isFirstSelection) {
                    isFirstSelection = false
                    return
                }
                sharedPreferences.edit().putInt("mainBgmSelection", position).apply()
                Log.d("Settings", "使用者選擇 BGM: $position")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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
            Log.d("OSM_TEST", "切換 OSM 開關為: $isChecked")
        }

        switchKeepScreenOn = findViewById(R.id.switchKeepScreenOn)
        switchKeepScreenOn.isChecked = sharedPreferences.getBoolean("keepScreenOn", true)
        switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("keepScreenOn", isChecked).apply()
        }
    }
}