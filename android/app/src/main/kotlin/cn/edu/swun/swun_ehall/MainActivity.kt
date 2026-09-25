package cn.edu.swun.swun_ehall

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import cn.edu.swun.swun_ehall.data.http.WebViewHost
import cn.edu.swun.swun_ehall.data.update.UpdateClient
import cn.edu.swun.swun_ehall.data.update.UpdateDownload
import cn.edu.swun.swun_ehall.ui.SwunApp
import cn.edu.swun.swun_ehall.ui.theme.applyPredictiveBack
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val skipSplash = SwunApplication.pickingMedia
        SwunApplication.markPickingMedia(application, false)
        WebViewHost.bind(this)
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
        enableEdgeToEdge()
        applyPredictiveBack(this, getSharedPreferences("swun_theme", 0).getBoolean("predictiveBack", false))
        setContent { SwunApp(playSplash = savedInstanceState == null && !skipSplash) }
        handleUpdateIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        WebViewHost.bind(this)
    }

    override fun onDestroy() {
        WebViewHost.unbind(this)
        super.onDestroy()
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action != UpdateDownload.ACTION_INSTALL) return
        val path = intent.getStringExtra(UpdateDownload.EXTRA_PATH).orEmpty()
        val file = File(path)
        if (file.exists()) UpdateClient.install(this, file)
    }
}
