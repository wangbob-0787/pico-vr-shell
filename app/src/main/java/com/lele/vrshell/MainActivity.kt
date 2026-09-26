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
        log("Surface 就绪 → 建虚拟显示器")
        try {
            val dm = getSystemService(DisplayManager::class.java)
            vd = dm.createVirtualDisplay(
                "emby-vd",
                VD_W, VD_H, VD_DPI,
                holder.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
            )
            log("虚拟显示器 OK  id=" + vd?.display?.displayId + "  " + VD_W + "x" + VD_H)
        } catch (t: Throwable) {
            log("建虚拟显示器失败: " + t.javaClass.simpleName + ": " + t.message)
            return
        }
        launchEmby()
    }

    private fun launchEmby() {
        val target = "tv.emby.embyatv"
        val intent = packageManager.getLaunchIntentForPackage(target)
        if (intent == null) {
            log("找不到 " + target + "（未安装？）")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val displayId = vd?.display?.displayId ?: -1
        if (displayId < 0) {
            log("虚拟显示器还没建好")
            return
        }

        val opts = ActivityOptions.makeBasic()
        try {
            // 用反射调用，避免编译期依赖 @SystemApi
            val m = ActivityOptions::class.java.getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
            m.invoke(opts, displayId)
            log("setLaunchDisplayId(" + displayId + ") 调用成功")
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            log("setLaunchDisplayId 失败: " + cause.javaClass.simpleName + ": " + cause.message)
            return
        }

        try {
            startActivity(intent, opts.toBundle())
            log("已发起启动 → 看下半屏出不出 Emby 画面")
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            log("startActivity 失败: " + cause.javaClass.simpleName + ": " + cause.message)
        }
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
