package com.example.pavewatchapp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * TouchableWrapper: Contenedor especial para el mapa que gestiona los eventos táctiles.
 * 
 * Principios POO:
 * - Herencia: Extiende FrameLayout.
 * - Sobrecarga de Constructores: Permite la instanciación programática y vía XML.
 * - Encapsulamiento de lógica de gestos: Evita que el ViewPager2 intercepte los toques del mapa.
 */
public class TouchableWrapper extends FrameLayout {

    // Constructor para instanciación programática
    public TouchableWrapper(@NonNull Context context) {
        super(context);
    }

    // Constructor necesario para inflar desde XML
    public TouchableWrapper(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    // Constructor necesario para inflar desde XML con estilos
    public TouchableWrapper(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // Solicitar al padre (ViewPager2) que no intercepte los eventos de movimiento
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Devolver el control al padre cuando el toque termina
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }
}
