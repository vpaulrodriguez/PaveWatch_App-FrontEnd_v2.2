package com.example.pavewatchapp;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * ViewPagerAdapter: gestiona los dos fragmentos del ViewPager2.
 *
 * Principios POO aplicados:
 *  - Herencia: extiende FragmentStateAdapter.
 *  - Encapsulamiento: la creación de fragmentos está centralizada aquí.
 */
public class ViewPagerAdapter extends FragmentStateAdapter {

    // Número total de pestañas
    private static final int NUM_TABS = 3;

    // Índices de cada pestaña — constantes para evitar "números mágicos"
    public static final int TAB_MONITOREO = 0;
    public static final int TAB_HISTORIAL  = 1;
    public static final int TAB_MAPA       = 2;

    public ViewPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    /**
     * Crea y retorna el Fragment correspondiente a cada posición.
     * Usa los factory methods estáticos de cada Fragment (POO: factory method).
     */
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case TAB_MONITOREO:
                return MonitoreoFragment.newInstance();
            case TAB_HISTORIAL:
                return HistorialFragment.newInstance();
            case TAB_MAPA:
                return MapaFragment.newInstance();
            default:
                return MonitoreoFragment.newInstance();
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }
}
