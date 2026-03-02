package com.example.module4_ex14

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.example.compass.CompassScreen

class MainActivity : ComponentActivity() {

    private lateinit var vm: CompassViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[CompassViewModel::class.java]

        setContent {
            CompassScreen(vm)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.start()
    }

    override fun onPause() {
        vm.stop()
        super.onPause()
    }
}
