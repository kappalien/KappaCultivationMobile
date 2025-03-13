package com.example.kappacultivationmobile

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchBackgroundSteps: Switch
    private lateinit var switchGPS: Switch
    private lateinit var seekBarDialogStep: SeekBar
    private lateinit var textViewDialogStep: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 綁定 UI 元件
        switchBackgroundSteps = findViewById(R.id.switchBackgroundSteps)
        switchGPS = findViewById(R.id.switchGPS)
        seekBarDialogStep = findViewById(R.id.seekBarDialogStep)
        textViewDialogStep = findViewById(R.id.textViewDialogStep)

        // 讀取儲存的設定值
        switchBackgroundSteps.isChecked = sharedPreferences.getBoolean("backgroundSteps", true)
        switchGPS.isChecked = sharedPreferences.getBoolean("gpsEnabled", true)
        val savedDialogStep = sharedPreferences.getInt("dialogStepInterval", 100)
        seekBarDialogStep.progress = savedDialogStep
        textViewDialogStep.text = "$savedDialogStep 步"

        // 當使用者改變開關或滑桿時，馬上更新設定值
        switchBackgroundSteps.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("backgroundSteps", isChecked).apply()
        }

        switchGPS.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("gpsEnabled", isChecked).apply()
        }

        seekBarDialogStep.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                textViewDialogStep.text = "$progress 步"
                sharedPreferences.edit().putInt("dialogStepInterval", progress).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}