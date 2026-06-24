package com.example.pavewatchapp;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel compartido entre MonitoreoFragment e HistorialFragment.
 *
 * Principios POO aplicados:
 *  - Herencia: extiende ViewModel de Jetpack Lifecycle.
 *  - Encapsulamiento: _listaBaches es privado; se expone solo
 *    la versión inmutable (LiveData) hacia el exterior.
 *  - Responsabilidad única: solo gestiona el estado de la lista de baches.
 */
public class BacheViewModel extends ViewModel {

    // Lista observable interna — mutable solo dentro del ViewModel
    private final MutableLiveData<List<Bache>> _listaBaches =
            new MutableLiveData<>(new ArrayList<>());

    /**
     * LiveData de solo lectura expuesto a los Fragments.
     * Los observers se suscriben aquí para recibir actualizaciones reactivas.
     */
    public LiveData<List<Bache>> getListaBaches() {
        return _listaBaches;
    }

    /**
     * Agrega un nuevo bache a la lista y notifica a todos los observers.
     * Crea una nueva lista para garantizar que LiveData detecte el cambio.
     *
     * @param bache El objeto Bache a agregar
     */
    public void agregarBache(Bache bache) {
        List<Bache> listaActual = new ArrayList<>();
        if (_listaBaches.getValue() != null) {
            listaActual.addAll(_listaBaches.getValue());
        }
        listaActual.add(bache);
        _listaBaches.setValue(listaActual);
    }

    /**
     * Retorna la cantidad actual de baches registrados en la sesión.
     */
    public int getCantidadBaches() {
        return _listaBaches.getValue() != null ? _listaBaches.getValue().size() : 0;
    }
}
