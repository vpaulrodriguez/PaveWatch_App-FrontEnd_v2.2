package com.example.pavewatchapp;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import Network.ApiClient;
import Network.ApiService;
import Network.BacheDTO;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapaFragment extends Fragment implements OnMapReadyCallback {
    private StorageReference mStorageRef;
    private GoogleMap mMap;
    private BacheViewModel bacheViewModel;
    private TileOverlay heatmapOverlay;

    private Dialog dialogoReporte;
    private ImageView imgDialogFoto;
    private Button btnDialogSubir;

    private Uri uriFotoSeleccionada;
    private Bitmap bitmapFotoTomada;

    // --- 1. LANZADORES DE ACTIVIDAD ---

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

    // --- 2. INTERFAZ Y DIÁLOGOS (sin cambios) ---

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

    private void mostrarBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_foto, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.btn_opcion_camara).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            verificarPermisosYAbrirCamara();
        });

        view.findViewById(R.id.btn_opcion_galeria).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            seleccionarFotoLauncher.launch("image/*");
        });

        bottomSheetDialog.show();
    }

    private void verificarPermisosYAbrirCamara() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                tomarFotoLauncher.launch(null);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Error al abrir la cámara", Toast.LENGTH_SHORT).show();
            }
        } else {
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

    // --- 3. FIREBASE Y BACKEND (sin cambios) ---

    private void subirFotoAFirebase(Uri rutaUri) {
        String nombre = "baches/foto_" + System.currentTimeMillis() + ".jpg";
        StorageReference ref = mStorageRef.child(nombre);
        ref.putFile(rutaUri).addOnSuccessListener(task -> obtenerUrlYEnviar(ref))
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void subirBitmapAFirebase(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
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

            String payload = String.format(Locale.US,
                    "{\"latitud\": %f, \"longitud\": %f, \"severidad\": 0.0, \"tipo_evento\": \"REPORTE_FOTO\", \"dispositivo\": \"app_FOTO\", \"fotoUrl\": \"%s\"}",
                    lat, lon, uri.toString());

            MqttManager.getInstance().publicarMensaje(requireContext(), payload, new MqttManager.MqttCallback() {
                @Override
                public void onExito() {
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

        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        bacheViewModel.getListaBaches().observe(getViewLifecycleOwner(), this::actualizarMarcadores);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-12.046374, -77.042793), 14));

        cargarMapaDeCalor();
    }

    private void actualizarMarcadores(List<Bache> baches) {
        if (mMap == null) return;
        // Los pines de sesión siguen desactivados a propósito (decisión previa
        // para no saturar el mapa); el heatmap de abajo cubre esa necesidad
        // con datos agregados de la base de datos.
    }

    // --- 5. MAPA DE CALOR ---

    private void cargarMapaDeCalor() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.getMapaCalor().enqueue(new Callback<List<BacheDTO>>() {
            @Override
            public void onResponse(Call<List<BacheDTO>> call, Response<List<BacheDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    pintarMapaDeCalor(response.body());
                } else {
                    Log.e("MapaCalor", "Respuesta no exitosa: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<BacheDTO>> call, Throwable t) {
                Log.e("MapaCalor", "Error al cargar mapa de calor", t);
            }
        });
    }

    private void pintarMapaDeCalor(List<BacheDTO> baches) {
        if (mMap == null) return;

        List<WeightedLatLng> puntos = new ArrayList<>();
        for (BacheDTO b : baches) {
            if (b.getLatitud() != null && b.getLongitud() != null) {
                double peso = b.getSeveridad() != null ? b.getSeveridad() : 1.0;

                // --- TRUCO MATEMÁTICO ---
                // Elevamos la severidad al cuadrado para forzar que los baches
                // graves aislados destaquen en color rojo.
                peso = Math.pow(peso, 2);

                puntos.add(new WeightedLatLng(new LatLng(b.getLatitud(), b.getLongitud()), peso));
            }
        }
        if (puntos.isEmpty()) return;

        if (heatmapOverlay != null) {
            heatmapOverlay.remove();
        }

        HeatmapTileProvider provider = new HeatmapTileProvider.Builder()
                .weightedData(puntos)
                .radius(40)
                .build();

        heatmapOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(provider));
    }
}