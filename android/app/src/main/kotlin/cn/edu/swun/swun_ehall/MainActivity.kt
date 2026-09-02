package cn.edu.swun.swun_ehall

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        LocatePlugin.registerWith(flutterEngine, this)
        VaultPlugin.registerWith(flutterEngine)
    }
}
