package com.lele.vrshell

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VR 壳探针 v4
 *  - Emby 铺满整个虚拟屏（调试区默认隐藏，点左上角热点可唤出）
 *  - 尝试把手柄/触摸事件转发进虚拟显示器
 */
class MainActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private var vd: VirtualDisplay? = null
    private val ui = Handler(Looper.getMainLooper())
    private val lines = ArrayDeque<String>()

    private fun log(msg: String) {
        Log.i(TAG, msg)
        ui.post {
            lines.addLast("[" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "] " + msg)
            while (lines.size > 6) lines.removeFirst()
            status.text = lines.joinToString("\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        // 虚拟显示器的画面：铺满整屏
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        surfaceView.isFocusable = true
        surfaceView.isFocusableInTouchMode = true
        root.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 状态浮层：默认隐藏，点左上角唤出 6 秒
        status = TextView(this).apply {
            setTextColor(0xFF33FF33.toInt())
            textSize = 10f
            setBackgroundColor(0xCC000000.toInt())
            setPadding(12, 8, 12, 8)
            visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.START
        root.addView(status, lp)

        // 左上角 80x80 热点：点它切换调试浮层
        val hot = View(this)
        hot.setOnClickListener {
            status.visibility = if (status.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (status.visibility == View.VISIBLE) {
                ui.removeCallbacksAndMessages(null)
                ui.postDelayed({ status.visibility = View.GONE }, 6000)
            }
        }
        val hlp = FrameLayout.LayoutParams(80, 80)
        hlp.gravity = Gravity.TOP or Gravity.START
        root.addView(hot, hlp)

        setContentView(root)

        log("v4 启动 targetSdk=" + applicationInfo.targetSdkVersion)

        // 触摸转发：把落在 SurfaceView 上的事件送进虚拟显示器
        surfaceView.setOnTouchListener { _, ev ->
            forwardTouch(ev)
            true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        log("Surface 就绪")
        try {
            val dm = getSystemService(DisplayManager::class.java)
            vd = dm.createVirtualDisplay("vd", VD_W, VD_H, VD_DPI, holder.surface, 0)
            log("显示器 id=" + vd?.display?.displayId)
        } catch (t: Throwable) {
            log("建显示器失败: " + t.javaClass.simpleName + " " + (t.message ?: "").take(60))
            return
        }
        launchEmby()
    }

    private fun launchEmby() {
        val intent = packageManager.getLaunchIntentForPackage("tv.emby.embyatv")
        if (intent == null) { log("没有 Emby"); return }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val d = vd ?: return
        val opts = ActivityOptions.makeBasic()
        try {
            ActivityOptions::class.java
                .getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
                .invoke(opts, d.display.displayId)
        } catch (t: Throwable) {
            log("setLaunchDisplayId 失败: " + ((t.cause ?: t).message ?: "").take(60)); return
        }
        try {
            startActivity(intent, opts.toBundle())
            log("已启动 Emby")
        } catch (t: Throwable) {
            log("startActivity 失败: " + ((t.cause ?: t).message ?: "").take(60))
        }
    }

    /** 把事件转投到虚拟显示器：先缩放到虚拟显示器坐标，再尝试注入 */
    private fun forwardTouch(ev: MotionEvent) {
        val d = vd ?: return
        val w = surfaceView.width.takeIf { it > 0 } ?: return
        val h = surfaceView.height.takeIf { it > 0 } ?: return
        val copy = MotionEvent.obtain(ev)
        copy.setLocation(ev.x * VD_W / w, ev.y * VD_H / h)
        try {
            try {
                InputEvent::class.java
                    .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                    .invoke(copy, d.display.displayId)
            } catch (_: Throwable) { }

            val im = getSystemService("input")
            val m = im.javaClass.getMethod("injectInputEvent",
                InputEvent::class.java, Int::class.javaPrimitiveType)
            m.invoke(im, copy, 0)   // ASYNC
            log("转发 " + copy.action + " @" + copy.x.toInt() + "," + copy.y.toInt())
        } catch (t: Throwable) {
            log("注入失败: " + (t.cause ?: t).javaClass.simpleName + " " +
                ((t.cause ?: t).message ?: "").take(50))
        } finally {
            copy.recycle()
        }
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        vd?.release(); vd = null
    }

    override fun onDestroy() {
        super.onDestroy(); vd?.release(); vd = null
    }

    companion object {
        private const val TAG = "VRSHELL"
        private const val VD_W = 1280
        private const val VD_H = 720
        private const val VD_DPI = 240
    }
}
