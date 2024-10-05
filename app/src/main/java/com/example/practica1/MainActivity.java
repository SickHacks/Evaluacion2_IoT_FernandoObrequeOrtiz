package com.example.practica1;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView fechaHoraTextView;
    private EditText editTextNombre;
    private EditText editTextRut;
    private EditText editTextDescripcion;
    private Button btnGrabar;
    private Button btnCerrarAplicacion;
    private ScheduledExecutorService scheduler;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private boolean isAlertDialogShowing = false; // Flag para controlar el AlertDialog
    private long lastToastTime = 0; // Tiempo del último Toast
    private static final long TOAST_DEBOUNCE_TIME = 5000; // Tiempo en milisegundos para debounce

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

        editTextNombre = findViewById(R.id.nombreUsuario);
        editTextRut = findViewById(R.id.rutUsuario);
        editTextDescripcion = findViewById(R.id.editTextDescripcion);
        btnGrabar = findViewById(R.id.botonGuardar);
        fechaHoraTextView = findViewById(R.id.fechaHora);
        btnCerrarAplicacion = findViewById(R.id.botonCerrarApp);

        iniciarActualizacionHora();

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        // Inicializar el sensor
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
        if (sensors.size() > 0) {
            accelerometer = sensors.get(0);
        }

        btnGrabar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validarCampos()) {
                    mostrarConfirmacionGuardar();
                }
            }
        });

        btnCerrarAplicacion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Cierra la actividad actual
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this); // Desregistrar el listener
    }

    // Método para validar los campos
    private boolean validarCampos() {
        String nombre = editTextNombre.getText().toString().trim();
        String rut = editTextRut.getText().toString().trim();
        String descripcion = editTextDescripcion.getText().toString().trim();

        if (nombre.isEmpty()) {
            mostrarToast("Por favor, ingrese su nombre.");
            return false;
        }
        if (rut.isEmpty()) {
            mostrarToast("Por favor, ingrese su RUT.");
            return false;
        }
        if (!esRutValido(rut)) {
            mostrarToast("RUT inválido. Ingrese un RUT en formato correcto (sin puntos y con guion).");
            return false;
        }
        if (descripcion.isEmpty()) {
            mostrarToast("Por favor, ingrese una descripción.");
            return false;
        }
        return true; // Todos los campos son válidos
    }

    // Función que valida el RUT sin puntos y con guion
    private boolean esRutValido(String rut) {
        // Verificar que contenga solo un guion
        if (!rut.contains("-") || rut.chars().filter(ch -> ch == '-').count() != 1) {
            return false;
        }

        String[] rutParts = rut.split("-");
        if (rutParts.length != 2) {
            return false;
        }

        String rutBody = rutParts[0];
        String dv = rutParts[1].toUpperCase(); // Convertir a mayúsculas para comparar con 'K'

        // Validar que el cuerpo del RUT solo tenga números
        if (!rutBody.matches("\\d+")) {
            return false;
        }

        // Validar que el dígito verificador sea un número o 'K'
        if (!dv.matches("\\d|K")) {
            return false;
        }

        // Calcular dígito verificador esperado
        int suma = 0;
        int multiplicador = 2;

        for (int i = rutBody.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(rutBody.charAt(i)) * multiplicador;
            multiplicador = multiplicador == 7 ? 2 : multiplicador + 1;
        }

        int resto = 11 - (suma % 11);
        String dvEsperado;
        if (resto == 11) {
            dvEsperado = "0";
        } else if (resto == 10) {
            dvEsperado = "K";
        } else {
            dvEsperado = String.valueOf(resto);
        }

        return dv.equals(dvEsperado);
    }

    // Función para iniciar la actualización de la hora cada minuto
    private void iniciarActualizacionHora() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(() -> mostrarFechaYHora());
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void mostrarFechaYHora() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy | HH:mm", Locale.getDefault());
        String currentDateAndTime = dateFormat.format(calendar.getTime());
        fechaHoraTextView.setText(currentDateAndTime);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Verificar si el eje Y es mayor a 8
        if (event.values[1] > 9 && !isAlertDialogShowing) {
            if (validarCampos()) {
                mostrarConfirmacionGuardar();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Este método puede permanecer vacío
    }

    // Método para mostrar el AlertDialog de confirmación
    private void mostrarConfirmacionGuardar() {
        isAlertDialogShowing = true; // Establecer el flag a true

        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Confirmar Guardar")
                .setMessage("¿Está seguro de que desea grabar los datos?")
                .setPositiveButton("Sí", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mostrarToast("DATOS GRABADOS\n¡¡¡ES INCREÍBLE!!!");
                        isAlertDialogShowing = false; // Restablecer el flag al cerrar el dialog
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        isAlertDialogShowing = false; // Restablecer el flag al cerrar el dialog
                    }
                })
                .show();
    }

    // Método para mostrar Toast con debounce
    private void mostrarToast(String message) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastToastTime > TOAST_DEBOUNCE_TIME) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            lastToastTime = currentTime; // Actualizar el tiempo del último Toast
        }
    }
}
