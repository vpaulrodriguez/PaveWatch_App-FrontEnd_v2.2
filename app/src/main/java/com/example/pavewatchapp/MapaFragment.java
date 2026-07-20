package com.example.pavewatchapp;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

public class MapaFragment extends Fragment implements OnMapReadyCallback {
    private StorageReference mStorageRef;
    private GoogleMap mMap;
    private BacheViewModel bacheViewModel;

    private Dialog dialogoReporte;
    private ImageView imgDialogFoto;
    private Button btnDialogSubir;

    private Uri uriFotoSeleccionada;
    private Bitmap bitmapFotoTomada;

    // --- 1. LANZADORES DE ACTIVIDAD ---

    // Lanzador para la galería
    private final ActivityResultLauncher<String> seleccionarFotoLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uriFotoSeleccionada = uri;
                    bitmapFotoTomada = null;
                    prepararVistaPrevia();
                    imgDialogFoto.setImageURI(uri);
                }
            }
    );

    // Lanzador para la cámara (Thumbnail)
    private final ActivityResultLauncher<Void> tomarFotoLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) {
                    bitmapFotoTomada = bitmap;
                    uriFotoSeleccionada = null;
                    prepararVistaPrevia();
                    imgDialogFoto.setImageBitmap(bitmap);
                }
            }
    );

    // NUEVO: Lanzador para pedir permisos de cámara en tiempo real
    private final ActivityResultLauncher<String> solicitarPermisoCamara = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    tomarFotoLauncher.launch(null);
                } else {
                    Toast.makeText(requireContext(), "Se requiere permiso de cámara para tomar fotos", Toast.LENGTH_SHORT).show();
                }
            }
    );

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
        mStorageRef = FirebaseStorage.getInstance().getReference();

        FloatingActionButton fabCamera = view.findViewById(R.id.fab_camera);
        fabCamera.setOnClickListener(v -> mostrarDialogoReporte());

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        bacheViewModel = new ViewModelProvider(requireActivity()).get(BacheViewModel.class);
    }

    // --- 2. INTERFAZ Y DIÁLOGOS ---

    private void mostrarDialogoReporte() {
        dialogoReporte = new Dialog(requireContext());
        dialogoReporte.setContentView(R.layout.dialog_reportar_bache);
        dialogoReporte.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        imgDialogFoto = dialogoReporte.findViewById(R.id.img_foto_bache);
        btnDialogSubir = dialogoReporte.findViewById(R.id.btn_subir_foto);
        ImageButton btnCerrar = dialogoReporte.findViewById(R.id.btn_cerrar_dialog);

        btnDialogSubir.setEnabled(false);
        btnDialogSubir.setBackgroundColor(Color.parseColor("#AAAAAA"));
        uriFotoSeleccionada = null;
        bitmapFotoTomada = null;

        // Al tocar la imagen gris, abrimos el BottomSheet en lugar del AlertDialog viejo
        imgDialogFoto.setOnClickListener(v -> mostrarBottomSheet());

        btnCerrar.setOnClickListener(v -> dialogoReporte.dismiss());

        btnDialogSubir.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Subiendo foto...", Toast.LENGTH_SHORT).show();
            btnDialogSubir.setEnabled(false);
            if (uriFotoSeleccionada != null) subirFotoAFirebase(uriFotoSeleccionada);
            else if (bitmapFotoTomada != null) subirBitmapAFirebase(bitmapFotoTomada);
            dialogoReporte.dismiss();
        });

        dialogoReporte.show();
    }

    // NUEVO: Método que despliega el menú inferior elegante
    private void mostrarBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_foto, null);
        bottomSheetDialog.setContentView(view);

        // Click en Cámara
        view.findViewById(R.id.btn_opcion_camara).setOnClickListener(v -> {
            bottomSheetDialog.dismiss(); // Cierra el menú inferior
            verificarPermisosYAbrirCamara();
        });

        // Click en Galería
        view.findViewById(R.id.btn_opcion_galeria).setOnClickListener(v -> {
            bottomSheetDialog.dismiss(); // Cierra el menú inferior
            seleccionarFotoLauncher.launch("image/*");
        });

        bottomSheetDialog.show();
    }

    private void verificarPermisosYAbrirCamara() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Ya tiene permiso, abre la cámara directo
            try {
                tomarFotoLauncher.launch(null);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Error al abrir la cámara", Toast.LENGTH_SHORT).show();
            }
        } else {
            // No tiene permiso, lo pedimos en pantalla
            solicitarPermisoCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void prepararVistaPrevia() {
        if (imgDialogFoto != null) {
            imgDialogFoto.setImageTintList(null);
            imgDialogFoto.setPadding(0, 0, 0, 0);
            imgDialogFoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        if (btnDialogSubir != null) {
            btnDialogSubir.setEnabled(true);
            btnDialogSubir.setBackgroundColor(Color.parseColor("#00FF66"));
            btnDialogSubir.setTextColor(Color.BLACK);
        }
    }

    // --- 3. FIREBASE Y BACKEND ---

    private void subirFotoAFirebase(Uri rutaUri) {
        String nombre = "baches/foto_" + System.currentTimeMillis() + ".jpg";
        StorageReference ref = mStorageRef.child(nombre);
        ref.putFile(rutaUri).addOnSuccessListener(task -> obtenerUrlYEnviar(ref))
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void subirBitmapAFirebase(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Ojo aquí: el bitmap que devuelve este método es un "Thumbnail" (miniatura)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        String nombre = "baches/foto_" + System.currentTimeMillis() + ".jpg";
        StorageReference ref = mStorageRef.child(nombre);
        ref.putBytes(baos.toByteArray()).addOnSuccessListener(task -> obtenerUrlYEnviar(ref))
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void obtenerUrlYEnviar(StorageReference fotoRef) {
        fotoRef.getDownloadUrl().addOnSuccessListener(uri -> {
            double lat = mMap.getCameraPosition().target.latitude;
            double lon = mMap.getCameraPosition().target.longitude;

            // JSON CORREGIDO: Agregamos "severidad" (0.0) y "dispositivo" ("app_FOTO")
            // para que Jackson en Spring Boot no colapse buscando campos vacíos.
            String payload = String.format(Locale.US,
                    "{\"latitud\": %f, \"longitud\": %f, \"severidad\": 0.0, \"tipo_evento\": \"REPORTE_FOTO\", \"dispositivo\": \"app_FOTO\", \"fotoUrl\": \"%s\"}",
                    lat, lon, uri.toString());

            MqttManager.getInstance().publicarMensaje(requireContext(), payload, new MqttManager.MqttCallback() {
                @Override
                public void onExito() {
                    // TEXTO CAMBIADO AQUÍ
                    Toast.makeText(requireContext(), "¡Bache detectado y reportado!", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String mensaje) {
                    // Errores ya manejados por MqttManager
                }
            });
        });
    }

    // --- 4. MAPA ---

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Ocultar los dos iconos de abajo a la derecha (Map Toolbar)
        mMap.getUiSettings().setMapToolbarEnabled(false);

        // Mostrar el botón nativo de "Mi ubicación" (Círculo azul arriba a la derecha)
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Opcional: Ocultar los botones de Zoom (+ y -) si quieres el mapa más limpio
        // mMap.getUiSettings().setZoomControlsEnabled(false);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true); // Esto dibuja tu punto azul en el mapa
        }

        bacheViewModel.getListaBaches().observe(getViewLifecycleOwner(), this::actualizarMarcadores);

        // Coordenadas por defecto (puedes cambiar esto luego para que inicie en la ubicación actual del usuario)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-12.046374, -77.042793), 14));
    }

    private void actualizarMarcadores(List<Bache> baches) {
        if (mMap == null) return;

        // Limpiamos el mapa por si acaso
        mMap.clear();

        // ✨ 3. Quitamos el código que dibujaba los pines rojos.
        // Lo dejamos comentado por si en el futuro decides mostrar otro tipo de icono.
        /*
        for (Bache bache : baches) {
            LatLng pos = new LatLng(bache.getLatitud(), bache.getLongitud());
            mMap.addMarker(new MarkerOptions().position(pos).title(bache.getTipo()));
        }
        */
    }
}