package com.example.kappacultivationmobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class AchievementsActivity : AppCompatActivity() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: AchievementPagerAdapter
    private lateinit var achievementManager: AchievementManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.achievement_tabs)

        tabLayout = findViewById(R.id.achievementTabLayout)
        viewPager = findViewById(R.id.achievementViewPager)

        // 初始化成就管理器
        achievementManager = AchievementManager(this)

        val achievementProvider = object : IAchievementProvider {
            override fun getAchievements(): List<AchievementDefinition> =
                achievementManager.getAllAchievements()
        }

        adapter = AchievementPagerAdapter(this, achievementProvider) { /* 點擊事件可留空 */ }
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
    }
}

interface IAchievementProvider {
    fun getAchievements(): List<AchievementDefinition>
}
