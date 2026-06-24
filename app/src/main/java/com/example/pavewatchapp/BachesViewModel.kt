package com.example.pavewatchapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BacheViewModel : ViewModel() {
    // Lista interna protegida (aquí guardamos los baches en vivo)
    private val _listaBaches = MutableLiveData<MutableList<Bache>>(mutableListOf())

    // Lista pública que la pestaña Historial va a estar "vigilando"
    val listaBaches: LiveData<MutableList<Bache>> get() = _listaBaches

    // Función para meter baches desde la pestaña de Monitoreo
    fun agregarBache(bache: Bache) {
        val listaActual = _listaBaches.value ?: mutableListOf()
        listaActual.add(bache)
        _listaBaches.value = listaActual // Esto notifica automáticamente el cambio
    }
}