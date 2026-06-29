package com.example.pavewatchapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * MainActivity — Actividad contenedora principal (Single Activity Architecture).
 *
 * Principios POO aplicados:
 *  - Herencia: extiende AppCompatActivity.
 *  - Composición: delega la gestión de fragmentos al ViewPagerAdapter.
 *  - Responsabilidad única: solo configura el contenedor y las pestañas.
 */
public class MainActivity extends AppCompatActivity {

    // Nombres de las pestañas mostrados en el TabLayout
    private static final String[] TAB_TITLES = {"MONITOREO", "HISTORIAL", "MAPA"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configurarNavegacion();
    }

    /**
     * Enlaza el TabLayout con el ViewPager2 usando TabLayoutMediator.
     * Cada pestaña recibe su título del arreglo TAB_TITLES.
     */
    private void configurarNavegacion() {
        ViewPager2 viewPager2 = findViewById(R.id.viewPager);
        TabLayout        tabLayout  = findViewById(R.id.tabLayout);
        ViewPagerAdapter adapter    = new ViewPagerAdapter(this);

        viewPager2.setAdapter(adapter);

        // Sincroniza los títulos de las pestañas con el deslizamiento del ViewPager2
        new TabLayoutMediator(tabLayout, viewPager2,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();
    }
}
