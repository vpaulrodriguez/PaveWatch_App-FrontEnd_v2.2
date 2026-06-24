package com.example.pavewatchapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider // IMPORTANTE: Agrega este import

class MonitoreoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_monitoreo, container, false)

        // Enlazamos con el almacén compartido de la actividad principal
        val bacheViewModel = ViewModelProvider(requireActivity()).get(BacheViewModel::class.java)

        val btnReportar = view.findViewById<Button>(R.id.btnReportar)
        val switchAuto = view.findViewById<SwitchCompat>(R.id.switch1)

        btnReportar.setOnClickListener {
            // 1. Creamos el objeto con los datos del bache manual
            val nuevoBache = Bache("Reporte Manual", "03:15 AM", "Enviado a MQTT")

            // 2. Lo mandamos al almacén central
            bacheViewModel.agregarBache(nuevoBache)

            // 3. Mostramos la alerta en pantalla
            Toast.makeText(requireContext(), "¡Reporte de bache enviado manualmente!", Toast.LENGTH_SHORT).show()
        }

        switchAuto.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(requireContext(), "Detección por acelerómetro: ACTIVADA", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Detección automática en pausa", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}