package com.example.module4_ex10

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                LocationAddressScreen()
            }
        }
    }
}

@Composable
private fun LocationAddressScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current

    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var isLoading by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf("Нажмите кнопку") }
    var latLngText by remember { mutableStateOf("") }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PermissionChecker.PERMISSION_GRANTED || coarse == PermissionChecker.PERMISSION_GRANTED
    }

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val net = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        return gps || net
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)

        if (!granted) {
            addressText = "Вы отказали в разрешении на геолокацию."
            latLngText = ""
            isLoading = false
        }
        // если granted — пользователь нажмёт кнопку ещё раз (проще и понятнее)
    }

    suspend fun getAddressByLatLng(lat: Double, lng: Double): String {
        val geocoder = Geocoder(context, Locale.getDefault())

        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { list ->
                        val a = list.firstOrNull()
                        val line = a?.getAddressLine(0)
                        cont.resume(line ?: "Адрес не найден (пустой результат).")
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val list = geocoder.getFromLocation(lat, lng, 1)
                    val a = list?.firstOrNull()
                    a?.getAddressLine(0) ?: "Адрес не найден (пустой результат)."
                }
            }
        } catch (e: IOException) {
            // Обычно тут “нет интернета / сервис недоступен”
            "Не удалось получить адрес. Проверьте интернет. (${e.localizedMessage ?: "IOException"})"
        } catch (e: Exception) {
            "Ошибка геокодирования: ${e.localizedMessage ?: e::class.java.simpleName}"
        }
    }

    suspend fun loadLocationAndAddress() {
        if (!hasLocationPermission()) {
            addressText = "Нужны разрешения на геолокацию."
            latLngText = ""
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        if (!isLocationEnabled()) {
            addressText = "Геолокация выключена (GPS/Сеть). Включите её в настройках."
            latLngText = ""
            return
        }

        isLoading = true
        addressText = "Загрузка..."
        latLngText = ""

        try {
            val cts = CancellationTokenSource()

            // Самый понятный одноразовый запрос свежей локации
            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).awaitOrNull()

            if (location == null) {
                addressText = "Не удалось получить координаты (location = null). Попробуйте ещё раз."
                latLngText = ""
                return
            }

            val lat = location.latitude
            val lng = location.longitude
            latLngText = "Lat: ${"%.6f".format(lat)}\nLng: ${"%.6f".format(lng)}"

            // reverse geocoding
            addressText = getAddressByLatLng(lat, lng)

        } catch (e: SecurityException) {
            addressText = "Нет разрешений (SecurityException)."
            latLngText = ""
        } catch (e: Exception) {
            addressText = "Ошибка получения локации: ${e.localizedMessage ?: e::class.java.simpleName}"
            latLngText = ""
        } finally {
            isLoading = false
        }
    }

    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = addressText,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            if (latLngText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = latLngText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { scope.launch { loadLocationAndAddress() } },
                enabled = !isLoading
            ) {
                Text("Получить мой адрес")
            }

            if (isLoading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
    }
