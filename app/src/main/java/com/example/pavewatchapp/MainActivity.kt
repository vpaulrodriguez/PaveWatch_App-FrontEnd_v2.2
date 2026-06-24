package com.example.pavewatchapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Enlazamos los componentes de tu activity_main.xml
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        // 2. Configuramos el adaptador de las pestañas
        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        // 3. Sincronizamos las pestañas con el deslizador
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "MONITOREO"
                else -> "HISTORIAL"
            }
        }.attach()
    }
}