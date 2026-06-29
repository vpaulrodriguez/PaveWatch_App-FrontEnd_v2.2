package com.example.pavewatchapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MapaFragment: Muestra la ubicación en tiempo real y marcadores de baches.
 * Permite también el registro manual mediante un puntero visual que se activa bajo demanda.
 */
public class MapaFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private BacheViewModel bacheViewModel;

    // Componentes de la interfaz
    private ExtendedFloatingActionButton fabActivarSeleccion;
    private ExtendedFloatingActionButton fabRegistrarConfirmar;
    private FloatingActionButton fabCancelarSeleccion;
    private ImageView imgPuntero;

    public static MapaFragment newInstance() {
        return new MapaFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mapa, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        enlazarComponentes(view);

        // Inicializar el mapa de forma asíncrona
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Obtener el ViewModel compartido
        bacheViewModel = new ViewModelProvider(requireActivity()).get(BacheViewModel.class);

        configurarListeners();
    }

    private void enlazarComponentes(View view) {
        fabActivarSeleccion = view.findViewById(R.id.fabActivarSeleccion);
        fabRegistrarConfirmar = view.findViewById(R.id.fabRegistrarConfirmar);
        fabCancelarSeleccion = view.findViewById(R.id.fabCancelarSeleccion);
        imgPuntero = view.findViewById(R.id.imgPuntero);
    }

    private void configurarListeners() {
        // Botón para entrar en modo selección
        fabActivarSeleccion.setOnClickListener(v -> configurarModoSeleccion(true));

        // Botón para cancelar selección
        fabCancelarSeleccion.setOnClickListener(v -> configurarModoSeleccion(false));

        // Botón para registrar el bache en la posición actual de la mira
        fabRegistrarConfirmar.setOnClickListener(v -> {
            if (mMap != null) {
                registrarBacheManual();
                configurarModoSeleccion(false);
            }
        });
    }

    /**
     * Alterna la visibilidad de los elementos según si el modo de selección está activo o no.
     * @param activo true para mostrar mira y confirmar, false para volver a vista limpia.
     */
    private void configurarModoSeleccion(boolean activo) {
        if (activo) {
            fabActivarSeleccion.hide();
            imgPuntero.setVisibility(View.VISIBLE);
            fabRegistrarConfirmar.show();
            fabCancelarSeleccion.show();
        } else {
            fabActivarSeleccion.show();
            imgPuntero.setVisibility(View.GONE);
            fabRegistrarConfirmar.hide();
            fabCancelarSeleccion.hide();
        }
    }

    private void registrarBacheManual() {
        LatLng centro = mMap.getCameraPosition().target;
        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        
        Bache nuevoBache = new Bache("Manual - Puntero Mapa", hora, "Local", centro.latitude, centro.longitude);
        bacheViewModel.agregarBache(nuevoBache);
        
        Toast.makeText(requireContext(), "Bache registrado con éxito", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Configuración básica del mapa
        mMap.getUiSettings().setZoomControlsEnabled(false); // Ocultar para UI más limpia
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);

        // Habilitar capa de ubicación si hay permisos
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        // Observar cambios en los baches para actualizar el mapa
        bacheViewModel.getListaBaches().observe(getViewLifecycleOwner(), this::actualizarMarcadores);

        // Posición inicial: Lima, Perú
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-12.046374, -77.042793), 14));
    }

    private void actualizarMarcadores(List<Bache> baches) {
        if (mMap == null) return;

        mMap.clear();

        for (Bache bache : baches) {
            LatLng posicion = new LatLng(bache.getLatitud(), bache.getLongitud());
            
            float color;
            if (bache.getTipo().contains("Manual")) {
                color = BitmapDescriptorFactory.HUE_ORANGE;
            } else if (bache.getTipo().contains("Automático")) {
                color = BitmapDescriptorFactory.HUE_RED;
            } else {
                color = BitmapDescriptorFactory.HUE_YELLOW;
            }

            mMap.addMarker(new MarkerOptions()
                    .position(posicion)
                    .title(bache.getTipo())
                    .snippet("Hora: " + bache.getHora())
                    .icon(BitmapDescriptorFactory.defaultMarker(color)));
        }
    }
}
