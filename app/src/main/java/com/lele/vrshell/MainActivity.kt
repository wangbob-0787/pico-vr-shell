package com.lele.vrshell

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最小验证探针：能不能把官方 Emby 拉到我们自己建的虚拟显示器上。
 *
 * 通过 = 下方 SurfaceView 里出现 Emby 的界面 → VR 影院方案成立
 * 不通过 = 日志区给出具体异常 → 走备选方案
 */
class MainActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var logView: TextView
    private lateinit var surfaceView: SurfaceView
    private var vd: VirtualDisplay? = null

    private fun log(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread {
            logView.append("[" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "] " + msg + "\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101010.toInt())
        }

        logView = TextView(this).apply {
            setTextColor(0xFF33FF33.toInt())
            textSize = 10f
            setPadding(16, 16, 16, 8)
        }
        root.addView(ScrollView(this).apply { addView(logView) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 45f))

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        root.addView(surfaceView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 55f))

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER }
        bar.addView(Button(this).apply {
            text = "重新拉 Emby"
            setOnClickListener { launchEmby() }
        })
        bar.addView(Button(this).apply {
            text = "退出"
            setOnClickListener { finish() }
        })
        root.addView(bar)

        setContentView(root)

        log("探针启动")
        log("targetSdk = " + applicationInfo.targetSdkVersion + "  (28 可绕开副显示器限制)")
        log("包名 = " + packageName)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        log("Surface 就绪 → 遍历标志组合建虚拟显示器")

        // 逐个试：不同标志组合的权限要求不同
        val variants = listOf(
            Triple("无标志(0)", 0, "最宽松，看能否承载别的 App"),
            Triple("OWN_CONTENT_ONLY", DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                "只放自己的内容，不需权限"),
            Triple("PUBLIC", DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                "允许别的 App(需系统权限)"),
            Triple("PUBLIC|OWN_CONTENT_ONLY",
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                "组合")
        )

        val dm = getSystemService(DisplayManager::class.java)
        for ((name, flags, note) in variants) {
            try {
                val d = dm.createVirtualDisplay("vd-$name", VD_W, VD_H, VD_DPI,
                    holder.surface, flags)
                log("[" + name + "] 建成 id=" + d.display.displayId + " (" + note + ")")
                // 建成即停手：用它试拉 Emby
                vd = d
                launchEmby(name)
                return
            } catch (t: Throwable) {
                log("[" + name + "] 失败: " + t.javaClass.simpleName + ": " + (t.message ?: "").take(90))
            }
        }
        log("全部标志组合都失败 → 第三方 App 无法自建可承载别的 App 的显示器")
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        log("Surface 销毁，释放虚拟显示器")
        vd?.release()
        vd = null
    }

    override fun onDestroy() {
        super.onDestroy()
        vd?.release()
        vd = null
    }

    companion object {
        private const val TAG = "VRSHELL"
        private const val VD_W = 1280
        private const val VD_H = 720
        private const val VD_DPI = 240
    }
}
