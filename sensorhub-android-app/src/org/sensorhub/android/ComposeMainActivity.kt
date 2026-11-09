package org.sensorhub.android


import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.sensorhub.android.ui.SensorHubViewModel
import org.sensorhub.android.ui.theme.OSHTheme

class ComposeMainActivity : ComponentActivity() {
    private val runtime: SensorHubViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OSHTheme {
                OSHApp(runtime)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runtime.attach()
    }

    override fun onStop() {
        runtime.detach()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val controller = runtime.controller()
        if (controller != null && controller.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val controller = runtime.controller()
        if (controller != null && controller.onMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

}
