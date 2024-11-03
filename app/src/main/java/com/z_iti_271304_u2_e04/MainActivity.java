package com.z_iti_271304_u2_e04;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;

import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements Detector.DetectorListener {

    private boolean isFrontCamera = false;
    private Preview preview;
    private ImageAnalysis imageAnalyzer;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private Detector detector;
    private ExecutorService cameraExecutor;

    private PreviewView viewFinder;
    private OverlayView overlay;

    private static final String TAG = "Camera";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private TextView tvAmount;
    private TextView txtViewConfidence;
    private Slider sliderConfidence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa las vistas con findViewById
        viewFinder = findViewById(R.id.view_finder);
        overlay = findViewById(R.id.overlay);

        /*
        Intenta cargar el modelo
        Si falla el programa termina
        */
        try {
            detector = new Detector(this, Constants.MODEL_PATH, Constants.LABELS_PATH, this);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Error al cargar el modelo o las etiquetas: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Toast.makeText(getApplicationContext(), "Cerrando el programa...", Toast.LENGTH_LONG).show();
            System.exit(1);
        }

        detector.setup();

        tvAmount = findViewById(R.id.tvAmount);
        txtViewConfidence = findViewById(R.id.txtViewConfidence);

        sliderConfidence = findViewById(R.id.sliderConfidence);
        sliderConfidence.addOnChangeListener((slider, value, fromUser) -> {
            /*
            Función para detectar el cambio de valor de Slider
            Actuliza la cantidad de confianza del modelo
            */
            overlay.invalidate();
            detector.setConfidenceThreshold(value);
            txtViewConfidence.setText("Confianza del modelo: " + value * 100);
        });

        sliderConfidence.setLabelFormatter(new LabelFormatter() {
            /*
            Función para cambiar el formato en que se visualiza el valor actual del tick (el valor seleccionado)
            Cambia de decimal a entero para mejor comprensión
            Ej. 0.5 -> 50
            */
            @NonNull
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf(value * 100);
            }
        });

        //Verfica si los permisos se han concecido, si no los solicita
        if (allPermissionsGranted())
            startCamera();
        else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            if(!allPermissionsGranted()) Toast.makeText(getApplicationContext(), "La cámara no iniciará si no se le conceden permisos", Toast.LENGTH_LONG).show();
        }

        //Ejecuta el hilo de la camara
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        /*
        Función para actualizar la cámara
        */
        ProcessCameraProvider.getInstance(MainActivity.this).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(MainActivity.this).get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Error all intentar incializar la cámara", e);
            }
        }, ContextCompat.getMainExecutor(MainActivity.this));
    }


    private void bindCameraUseCases() {

        if (cameraProvider == null) throw new IllegalStateException("Camera initialization failed.");

        int rotation = viewFinder.getDisplay().getRotation();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
            Bitmap bitmapBuffer = Bitmap.createBitmap(
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
            imageProxy.close();

            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());

            if (isFrontCamera) {
                matrix.postScale(-1f, 1f, imageProxy.getWidth() / 2f, imageProxy.getHeight() / 2f);
            }

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.getWidth(), bitmapBuffer.getHeight(), matrix, true);

            detector.detect(rotatedBitmap);
        });

        cameraProvider.unbindAll();

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA))) {
                    startCamera();
                }
            });

    @Override
    protected void onDestroy() {
        /*

         */
        super.onDestroy();
        detector.clear();
        cameraExecutor.shutdown();
    }

    @Override
    protected void onResume() {
        /*
        Método para cuando la app se reanuda
         */
        super.onResume();
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    @Override
    public void onEmptyDetect() {

        tvAmount.setText("Cantidad: $0");

        List<BoundingBox> boundingBoxes = Collections.emptyList();
        overlay.setBoundingBoxes(boundingBoxes);
        overlay.invalidateDraw();
    }

    /*
    @Override
    public void onDetect(@NonNull List<BoundingBox> boundingBoxes) {
        runOnUiThread(() -> {

            //Cantidad de dinero
            float quantity = 0f;

            for(BoundingBox box: boundingBoxes) {
                String value = box.getClsName().replace("$", "");
                float c = Float.parseFloat(value);

                quantity += c;
            }

            tvAmount.setText("Cantidad: $" + quantity);

            overlay.setBoundingBoxes(boundingBoxes);
            overlay.invalidateDraw();
        });
    }
    */

    @Override
    public void onDetect(@NonNull List<BoundingBox> boundingBoxes) {
        runOnUiThread(() -> {
            // Cantidad de dinero
            float quantity = 0f;

            // Diámetros de referencia en mm
            float diameter1Peso = 21f; // Diámetro de 1 peso
            float diameter2Pesos = 23f; // Diámetro de 2 pesos
            float diameter5Pesos = 25.5f; // Diámetro de 5 pesos

            float referenceDiameter = 0f; // Para almacenar el diámetro de la moneda de referencia

            // Primero, detectamos la moneda de referencia
            for (BoundingBox box : boundingBoxes) {
                String value = box.getClsName().replace("$", "");

                if (value.equals("1") || value.equals("2") || value.equals("5")) {
                    // Obtener las dimensiones de la caja delimitadora
                    float width = box.getW(); // en píxeles
                    float height = box.getH(); // en píxeles
                    float detectedDiameter = (width + height) / 2; // Promedio para simular un diámetro

                    // Calcular la escala (escala en mm/píxel)
                    referenceDiameter = diameter1Peso; // Asumimos inicialmente que es 1 peso como referencia
                    if (value.equals("2")) {
                        referenceDiameter = diameter2Pesos;
                    } else if (value.equals("5")) {
                        referenceDiameter = diameter5Pesos;
                    }

                    // Guardar la escala para comparar después
                    float scale = referenceDiameter / detectedDiameter;

                    // Se rompe el bucle al encontrar la referencia
                    break;
                }
            }

            // Ahora, procesamos las demás monedas
            for (BoundingBox box : boundingBoxes) {
                String value = box.getClsName().replace("$", "");

                // Si no hemos encontrado una moneda de referencia, no procesamos más
                if (referenceDiameter == 0f) {
                    continue;
                }

                // Obtener las dimensiones de la caja delimitadora
                float width = box.getW(); // en píxeles
                float height = box.getH(); // en píxeles
                float detectedDiameter = (width + height) / 2; // Promedio para simular un diámetro

                // Calcular el tamaño real detectado
                float scale = referenceDiameter / detectedDiameter;
                float realDiameter = detectedDiameter * scale;

                // Comparar el tamaño detectado con los diámetros conocidos
                if (value.equals("125")) {
                    // Aquí asumimos que 125 representa una moneda de 1, 2 o 5 pesos, y determinamos su valor
                    if (realDiameter < diameter1Peso * 1.1) {
                        quantity += 1; // Moneda de 1 peso
                    } else if (realDiameter < diameter2Pesos * 1.1) {
                        quantity += 2; // Moneda de 2 pesos
                    } else {
                        quantity += 5; // Moneda de 5 pesos
                    }
                    continue; // Continuar a la siguiente caja delimitadora
                }

                // Convertir el valor de la moneda normal y agregar a la cantidad total
                float c = Float.parseFloat(value);
                quantity += c;
            }

            // Mostrar la cantidad total
            tvAmount.setText("Cantidad: $" + quantity);
            overlay.setBoundingBoxes(boundingBoxes);
            overlay.invalidateDraw();
        });
    }

}
