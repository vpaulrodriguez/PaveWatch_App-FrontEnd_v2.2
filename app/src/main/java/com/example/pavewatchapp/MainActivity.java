package com.example.pavewatchapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
    private static final String[] TAB_TITLES = {"HISTORIAL", "MAPA", "MONITOREO"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configurarNavegacion();
        crearCanalNotificaciones();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
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

        // para empezar en mapa
        viewPager2.setCurrentItem(1, false);

        // Sincroniza los títulos de las pestañas con el deslizamiento del ViewPager2
        new TabLayoutMediator(tabLayout, viewPager2,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();
    }

    private void crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    "CanalMonitoreo", // Este ID debe coincidir exactamente con el del MonitoreoService
                    "Monitoreo de Baches",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
        }
    }

}
