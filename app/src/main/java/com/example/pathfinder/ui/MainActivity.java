package com.example.pathfinder.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.pathfinder.R;
import com.example.pathfinder.detection.YoloNano;
import com.example.pathfinder.manager.Manager;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private OverlayView overlayView;
    private ExecutorService cameraExecutor;
    private TextView ttsStatus;
    private TextView fpsBox;
    private TextView latencyBox;

    private TextView permissionDeniedText;
    private Manager manager;
    private ExecutorService managerExecutor;

    private ArFragment arFragment;


    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher.
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Permissao da camera concedida", Toast.LENGTH_SHORT).show();
                    permissionDeniedText.setVisibility(View.GONE);
                    manager.startArCore();
                } else {
                    Toast.makeText(this, "Permissao da camera recusada", Toast.LENGTH_SHORT).show();
                    permissionDeniedText.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        permissionDeniedText = findViewById(R.id.permissionDeniedText);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);

        overlayView = findViewById(R.id.overlay);

        ttsStatus = findViewById(R.id.ttsStatus);
        fpsBox = findViewById(R.id.metricsFPSBox);
        latencyBox = findViewById(R.id.metricsLatencyBox);


        YoloNano detector = null;
        try {
            detector = new YoloNano(this);
        } catch (IOException e) {
            Log.e(e.getMessage(), "Failed to load model");
        }

        // Obter dimensões da tela para análise de risco
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        managerExecutor = Executors.newSingleThreadExecutor();
        manager = new Manager(this, detector, overlayView, arFragment, screenWidth, screenHeight);

        setupButtons();

        manager.getTtsInitialized().observe(this, isInitialized -> {
            if (isInitialized) {
                ttsStatus.setVisibility(View.GONE);
            } else {
                ttsStatus.setVisibility(View.VISIBLE);
            }
        });

        manager.getShowMetricsOnScreen().observe(this, showMetrics -> {
            if (showMetrics) {
                fpsBox.setVisibility(View.VISIBLE);
                latencyBox.setVisibility(View.VISIBLE);
            } else {
                fpsBox.setVisibility(View.GONE);
                latencyBox.setVisibility(View.GONE);
            }
        });

        manager.getFPS().observe(this, fps -> {
            fpsBox.setText(String.format("FPS: %.2f", fps));
        });

        manager.getLatency().observe(this, latency -> {
            latencyBox.setText(String.format("Latência: %.2fms", latency));
        });

        if (allPermissionsGranted()) {
            manager.startArCore();
        } else {
            permissionDeniedText.setVisibility(View.VISIBLE);
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupButtons() {
        // Find the buttons from the layout
        ImageButton onOffButton = findViewById(R.id.onOffButton);
        ImageButton soundButton = findViewById(R.id.soundButton);
        ImageButton repeatButton = findViewById(R.id.repeatButton);
        ImageButton metricsButton = findViewById(R.id.metricsButton);

        // Change the button images based on the manager's state
        manager.getShouldAlert().observe(this, shouldAlert -> {
            if (shouldAlert) {
                soundButton.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            } else {
                soundButton.setImageResource(android.R.drawable.ic_lock_silent_mode);
            }
        });

        // Set click listeners
        onOffButton.setOnClickListener(v -> {
            manager.toggleProcessing();
        });

        soundButton.setOnClickListener(v -> {
            manager.toggleTTS();
        });

        repeatButton.setOnClickListener(v -> {
            manager.repeatLastAlert();
        });

        metricsButton.setOnClickListener(v -> {
            manager.toggleMetricsOnScreen();
        });
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (arFragment != null) {
            try {
                arFragment.getArSceneView().resume();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Override
    protected void onPause() {
        if (arFragment != null) {
            arFragment.getArSceneView().pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (arFragment != null) {
            arFragment.getArSceneView().destroy();
        }
        super.onDestroy();
    }
}
