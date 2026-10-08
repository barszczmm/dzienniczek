package io.github.barszczmm.dzienniczek

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.barszczmm.dzienniczek.session.initAndroidDataStoreContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidDataStoreContext(this)
        setContent {
            App()
        }
    }
}
