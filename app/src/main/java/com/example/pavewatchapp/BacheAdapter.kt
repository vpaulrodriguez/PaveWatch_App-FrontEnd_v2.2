package com.example.pavewatchapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/**
 * Adaptador del historial de baches.
 * Usa ListAdapter + DiffUtil para aplicar solo los cambios necesarios
 * en la lista (animaciones eficientes, sin rebuildear toda la vista).
 */
class BacheAdapter : ListAdapter<Bache, BacheAdapter.BacheViewHolder>(BacheDiffCallback()) {

    // -------------------------------------------------------------------
    // ViewHolder: guarda referencias directas a los Views de cada ítem
    // para evitar llamadas costosas a findViewById en cada bind.
    // -------------------------------------------------------------------
    class BacheViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardBache)
        val tvTipo: TextView          = itemView.findViewById(R.id.tvTipo)
        val tvHora: TextView          = itemView.findViewById(R.id.tvHora)
        val chipEstado: Chip          = itemView.findViewById(R.id.chipEstado)
    }

    // -------------------------------------------------------------------
    // onCreateViewHolder: infla el layout XML de cada fila una sola vez.
    // -------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BacheViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bache, parent, false)
        return BacheViewHolder(view)
    }

    // -------------------------------------------------------------------
    // onBindViewHolder: mapea los datos del objeto Bache a la UI.
    // Llamado automáticamente por el RecyclerView cuando necesita
    // mostrar o reciclar una celda.
    // -------------------------------------------------------------------
    override fun onBindViewHolder(holder: BacheViewHolder, position: Int) {
        val bache = getItem(position) // getItem() es provisto por ListAdapter

        // Muestra el tipo del evento (ej. "Automático", "Manual")
        holder.tvTipo.text = bache.tipo

        // Muestra la hora de detección de forma secundaria
        holder.tvHora.text = bache.hora

        // El chip refleja el estado del envío MQTT
        holder.chipEstado.text = bache.estado

        // Colorea el chip según el estado para dar feedback visual rápido
        val chipColorRes = when {
            bache.estado.contains("Enviado", ignoreCase = true)  ->
                com.google.android.material.R.attr.colorPrimary
            bache.estado.contains("Error",   ignoreCase = true)  ->
                com.google.android.material.R.attr.colorError
            else ->
                com.google.android.material.R.attr.colorSecondary
        }

        val typedValue = android.util.TypedValue()
        holder.chipEstado.context.theme.resolveAttribute(chipColorRes, typedValue, true)
        holder.chipEstado.setChipBackgroundColorResource(
            android.R.color.transparent // se sobreescribe con el tint de abajo
        )
        holder.chipEstado.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(typedValue.data)
    }

    // -------------------------------------------------------------------
    // DiffCallback: le dice a ListAdapter cómo comparar elementos
    // antiguos vs. nuevos para calcular el diff de forma eficiente.
    // -------------------------------------------------------------------
    class BacheDiffCallback : DiffUtil.ItemCallback<Bache>() {

        /**
         * Compara identidad (¿es el mismo objeto en la lista?).
         * Aquí usamos hora como identificador único de sesión.
         */
        override fun areItemsTheSame(oldItem: Bache, newItem: Bache): Boolean {
            return oldItem.hora == newItem.hora
        }

        /**
         * Compara contenido (¿cambiaron los datos del ítem?).
         * data class genera equals() automáticamente comparando todos los campos.
         */
        override fun areContentsTheSame(oldItem: Bache, newItem: Bache): Boolean {
            return oldItem == newItem
        }
    }
}
