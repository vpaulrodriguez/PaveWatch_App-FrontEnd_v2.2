package com.example.pavewatchapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * HistorialFragment: pestaña que muestra el historial de baches detectados.
 *
 * Se engancha al BacheViewModel compartido a nivel de Actividad (requireActivity())
 * para recibir actualizaciones reactivas cada vez que MonitoreoFragment registra
 * un nuevo evento, sin ningún acoplamiento directo entre fragmentos.
 */
class HistorialFragment : Fragment() {

    // -------------------------------------------------------------------
    // Referencias a Views (inicializadas en onViewCreated para evitar
    // memory leaks si el fragment se destruye antes que la Activity).
    // -------------------------------------------------------------------
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvVacio: TextView
    private lateinit var bacheAdapter: BacheAdapter

    /**
     * ViewModel compartido con MonitoreoFragment.
     * Se obtiene usando requireActivity() como dueño del ciclo de vida,
     * lo que garantiza que ambos fragmentos reciben la misma instancia.
     */
    private lateinit var bacheViewModel: BacheViewModel

    // -------------------------------------------------------------------
    // Inflado del layout: solo retorna la vista, sin lógica de negocio.
    // -------------------------------------------------------------------
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_historial, container, false)
    }

    // -------------------------------------------------------------------
    // Configuración principal: se ejecuta cuando la vista ya existe en
    // memoria y es seguro buscar Views e inicializar observadores.
    // -------------------------------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Obtener referencias a los Views del layout
        recyclerView = view.findViewById(R.id.recyclerViewHistorial)
        tvVacio      = view.findViewById(R.id.tvHistorialVacio)

        // 2. Obtener el ViewModel compartido a nivel de la Activity contenedora
        bacheViewModel = ViewModelProvider(requireActivity())[BacheViewModel::class.java]

        // 3. Inicializar el adaptador (lista vacía por defecto)
        bacheAdapter = BacheAdapter()

        // 4. Configurar el RecyclerView con LayoutManager vertical y el adaptador
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter        = bacheAdapter
            // Optimización: el tamaño del RecyclerView no cambia cuando la lista muta
            setHasFixedSize(true)
        }

        // 5. Observar el LiveData: cada mutación de la lista dispara esta lambda
        observarBaches()
    }

    /**
     * Suscribe el fragmento al LiveData del ViewModel.
     * Cada vez que MonitoreoFragment llama a agregarBache(), este bloque
     * se ejecuta automáticamente en el hilo principal.
     */
    private fun observarBaches() {
        bacheViewModel.listaBaches.observe(viewLifecycleOwner) { listaBaches ->

            // Entregamos una copia inmutable a ListAdapter para que
            // DiffUtil calcule las diferencias de forma segura.
            bacheAdapter.submitList(listaBaches.toList())

            // Alterna la visibilidad entre el RecyclerView y el estado vacío
            actualizarEstadoVacio(listaBaches.isEmpty())
        }
    }

    /**
     * Muestra u oculta la vista de "sin datos" según el tamaño de la lista.
     *
     * @param listaVacia true si no hay baches registrados en la sesión.
     */
    private fun actualizarEstadoVacio(listaVacia: Boolean) {
        if (listaVacia) {
            recyclerView.visibility = View.GONE
            tvVacio.visibility      = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvVacio.visibility      = View.GONE
        }
    }

    companion object {
        /**
         * Factory method estático: forma idiomática de instanciar fragmentos
         * en Android para facilitar la lectura y posibles argumentos futuros.
         */
        fun newInstance(): HistorialFragment = HistorialFragment()
    }
}