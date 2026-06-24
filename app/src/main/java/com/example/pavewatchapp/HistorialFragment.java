package com.example.pavewatchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

/**
 * HistorialFragment: pestaña que muestra el listado de baches detectados.
 *
 * Principios POO aplicados:
 *  - Herencia: extiende Fragment de AndroidX.
 *  - Composición: usa BacheAdapter como colaborador encapsulado.
 *  - Observador (patrón): se suscribe al LiveData del ViewModel compartido.
 */
public class HistorialFragment extends Fragment {

    // ── Colaboradores ──────────────────────────────────────────────
    private BacheViewModel  bacheViewModel;
    private BacheAdapter    bacheAdapter;

    // ── Referencias a Views ────────────────────────────────────────
    private RecyclerView    recyclerViewHistorial;
    private LinearLayout    layoutVacio;
    private Chip            chipContador;

    // ── Ciclo de vida ──────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_historial, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Obtener el ViewModel compartido a nivel de Activity
        bacheViewModel = new ViewModelProvider(requireActivity())
                .get(BacheViewModel.class);

        // 2. Enlazar Views
        enlazarViews(view);

        // 3. Configurar RecyclerView
        configurarRecyclerView();

        // 4. Suscribirse al LiveData para actualizaciones reactivas
        observarBaches();
    }

    /**
     * Enlaza las referencias a los Views del fragment_historial.xml
     */
    private void enlazarViews(View view) {
        recyclerViewHistorial = view.findViewById(R.id.recyclerViewHistorial);
        layoutVacio           = view.findViewById(R.id.layoutVacio);
        chipContador          = view.findViewById(R.id.chipContador);
    }

    /**
     * Configura el RecyclerView con su LayoutManager y Adapter.
     */
    private void configurarRecyclerView() {
        bacheAdapter = new BacheAdapter();

        recyclerViewHistorial.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );
        recyclerViewHistorial.setAdapter(bacheAdapter);
        recyclerViewHistorial.setHasFixedSize(true);
    }

    /**
     * Se suscribe al LiveData del ViewModel.
     * Cada vez que MonitoreoFragment agrega un bache, este bloque
     * se ejecuta automáticamente en el hilo principal.
     *
     * BUG FIX #3: ahora el historial se actualiza correctamente
     * porque llamamos actualizarLista() con la nueva lista completa.
     */
    private void observarBaches() {
        bacheViewModel.getListaBaches().observe(getViewLifecycleOwner(), listaBaches -> {

            // Actualizar el adaptador con la nueva lista
            bacheAdapter.actualizarLista(listaBaches);

            // Actualizar el chip contador
            chipContador.setText(String.valueOf(listaBaches.size()));

            // Alternar entre lista y estado vacío
            actualizarEstadoVacio(listaBaches.isEmpty());
        });
    }

    /**
     * Muestra u oculta la vista de "sin datos" según si la lista está vacía.
     *
     * @param listaVacia true si no hay baches registrados
     */
    private void actualizarEstadoVacio(boolean listaVacia) {
        if (listaVacia) {
            recyclerViewHistorial.setVisibility(View.GONE);
            layoutVacio.setVisibility(View.VISIBLE);
        } else {
            recyclerViewHistorial.setVisibility(View.VISIBLE);
            layoutVacio.setVisibility(View.GONE);
        }
    }

    /**
     * Factory method estático — forma idiomática de instanciar Fragments.
     */
    public static HistorialFragment newInstance() {
        return new HistorialFragment();
    }
}
