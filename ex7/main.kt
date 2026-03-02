package com.example.module4_ex7
import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private var service: RandomNumberService? = null

    // ✅ теперь Compose будет замечать изменения
    private var isBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as RandomNumberService.LocalBinder
            service = b.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                // ✅ если сервис подключён — слушаем его flow
                val number by if (isBound && service != null) {
                    service!!.number.collectAsState(initial = null)
                } else {
                    remember { mutableStateOf(null) }
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = number?.toString() ?: "—", fontSize = 72.sp)

                        Spacer(Modifier.height(24.dp))

                        if (!isBound) {
                            Button(onClick = { bindToService() }) { Text("Подключиться") }
                        } else {
                            Button(onClick = { unbindFromService() }) { Text("Отключиться") }
                        }
                    }
                }
            }
        }
    }

    private fun bindToService() {
        bindService(Intent(this, RandomNumberService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindFromService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
            service = null
        }
    }

    override fun onDestroy() {
        unbindFromService()
        super.onDestroy()
    }
}
