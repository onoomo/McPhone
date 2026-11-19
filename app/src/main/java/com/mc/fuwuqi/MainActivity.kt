package com.mc.fuwuqi

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** 首页：标题去白 + 右上角新建 + 底部两胶囊（服务端 / 设置，设置内嵌直接显示） */
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var body: LinearLayout
    private lateinit var settingsHost: LinearLayout
    private lateinit var serverPage: ScrollView
    private lateinit var settingsPage: ScrollView
    private lateinit var tabServer: LinearLayout
    private lateinit var tabSet: LinearLayout
    private var tab = TAB_SERVER

    companion object {
        private const val TAB_SERVER = 0
        private const val TAB_SETTINGS = 1
    }

    override fun onCreate(b: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (getSharedPreferences("app", 0).getBoolean("dark", false))
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(b)
        installCrashHook()
        val root = Ui.linear(this, true)
        root.setBackgroundColor(getColor(R.color.bg))

        // 顶部：标题（去掉上方空白）+ 右上角「新建」胶囊
        val head = Ui.linear(this, false)
        head.setPadding(Ui.px(this, 22), Ui.px(this, 14), Ui.px(this, 22), Ui.px(this, 4))
        head.gravity = Gravity.CENTER_VERTICAL
        val titleCol = Ui.linear(this, true)
        titleCol.addView(Ui.text(this, "MC开服", 30f, getColor(R.color.text), true))
        val tag = Ui.text(this, getString(R.string.tagline), 14f, getColor(R.color.text_sub))
        tag.setPadding(0, Ui.px(this, 4), 0, 0)
        titleCol.addView(tag)
        head.addView(titleCol, Ui.lp(0, -2, 1f))
        // 右上角新建：浅绿圆底 + 绿加号
        val newPill = android.widget.FrameLayout(this)
        newPill.setBackgroundResource(R.drawable.bg_circle_pale)
        val newIv = ImageView(this)
        newIv.setImageResource(R.drawable.ic_plus)
        newIv.setColorFilter(getColor(R.color.green))
        val nip = android.widget.FrameLayout.LayoutParams(Ui.px(this, 22), Ui.px(this, 22))
        nip.gravity = Gravity.CENTER
        newPill.addView(newIv, nip)
        newPill.setOnClickListener { startActivity(Intent(this, NewServerActivity::class.java)) }
        head.addView(newPill, Ui.lp(Ui.px(this, 42), Ui.px(this, 42)))

        // 内容区：服务端列表 / 设置（FrameLayout 叠加，切换可见）
        body = Ui.linear(this, true)
        body.setPadding(Ui.px(this, 22), Ui.px(this, 12), Ui.px(this, 22), Ui.px(this, 12))
        serverPage = Ui.scroll(this)
        serverPage.addView(body, ViewGroup.LayoutParams(-1, -2))

        settingsHost = Ui.linear(this, true)
        settingsHost.setPadding(Ui.px(this, 20), Ui.px(this, 12), Ui.px(this, 20), Ui.px(this, 24))
        settingsPage = Ui.scroll(this)
        settingsPage.addView(settingsHost, ViewGroup.LayoutParams(-1, -2))

        val content = FrameLayout(this)
        content.addView(serverPage, FrameLayout.LayoutParams(-1, -1))
        content.addView(settingsPage, FrameLayout.LayoutParams(-1, -1))
        settingsPage.visibility = View.GONE

        val bottom = buildBottomBar()

        root.addView(head)
        root.addView(content, Ui.lp(-1, 0, 1f))
        root.addView(bottom)
        // 授权门：无存储权限先显示应用内授权页（参照物同款）
        if (!Ui.needStorageSilent(this)) {
            setContentView(permPage())
            return
        }
        setContentView(root)
        autoExtractContainer()
        switchTab(TAB_SERVER)
    }

    private var permShown = false

    private fun permPage(): android.view.View {
        permShown = true
        val c = Ui.linear(this, true)
        c.setBackgroundColor(getColor(R.color.bg))
        c.gravity = android.view.Gravity.CENTER
        val pad = Ui.px(this, 32)
        c.setPadding(pad, pad, pad, pad)
        val icon = android.widget.ImageView(this)
        icon.setImageResource(R.mipmap.ic_launcher)
        icon.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        c.addView(icon, Ui.lp(Ui.px(this, 96), Ui.px(this, 96)))
        val t = Ui.text(this, "MC开服", 24f, getColor(R.color.text), true)
        t.gravity = android.view.Gravity.CENTER
        t.setPadding(0, Ui.px(this, 20), 0, Ui.px(this, 8))
        c.addView(t)
        val sub = Ui.text(this, "开服前需要「所有文件访问权限」\n用于存放服务器文件、解压容器和运行核心", 15f, getColor(R.color.text_sub))
        sub.gravity = android.view.Gravity.CENTER
        c.addView(sub)
        val btn = Ui.text(this, "授予权限", 17f, Color.WHITE, true)
        btn.gravity = android.view.Gravity.CENTER
        btn.setBackgroundResource(R.drawable.bg_btn_green)
        val bp = Ui.lp(-1, Ui.px(this, 52))
        bp.topMargin = Ui.px(this, 36)
        c.addView(btn, bp)
        btn.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                try {
                    startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
        return c
    }

    override fun onResume() {
        super.onResume()
        if (permShown) {
            if (Ui.needStorageSilent(this)) { permShown = false; recreate() }
            return
        }
        if (Ui.needStorageSilent(this)) refreshCurrent()
        UiBus.subscribe { runOnUiThread { refreshCurrent() } }
    }

    override fun onPause() {
        super.onPause()
        UiBus.unsubscribe { refreshCurrent() }
    }

    private fun refreshCurrent() {
        if (tab == TAB_SETTINGS) SettingsUi.render(settingsHost, this) else render()
    }

    private fun switchTab(t: Int) {
        tab = t
        val server = t == TAB_SERVER
        serverPage.visibility = if (server) View.VISIBLE else View.GONE
        settingsPage.visibility = if (server) View.GONE else View.VISIBLE
        // 胶囊选中态：选中浅绿圆底 + 绿字，未选中透明 + 灰字
        styleTab(tabServer, server)
        styleTab(tabSet, !server)
        refreshCurrent()
    }

    private fun styleTab(t: LinearLayout, active: Boolean) {
        val pill = t.getChildAt(0)
        if (pill is android.widget.FrameLayout) {
            if (active) pill.setBackgroundResource(R.drawable.bg_circle_pale)
            else pill.setBackgroundResource(0)
        }
        val tv = t.getChildAt(1)
        if (tv is TextView) tv.setTextColor(getColor(if (active) R.color.green else R.color.text_sub))
        val iv = (pill as? android.widget.FrameLayout)?.getChildAt(0)
        if (iv is ImageView) iv.setColorFilter(getColor(if (active) R.color.green else R.color.text_sub))
    }

    fun render() {
        Store.ensureDirs()
        body.removeAllViews()
        addPermCard()
        if (ServerManager.runningId != null) {
            val cur = Store.findServer(ServerManager.runningId!!)
            if (cur != null) addServerCard(cur, true)
        }
        val arr = Store.loadServers()
        var any = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == ServerManager.runningId) { any = true; continue }
            any = true
            addServerCard(o, false)
        }
        if (!any && ServerManager.runningId == null) addEmpty()
        backfillBedrockVersion()
    }

    private var backfilling = false

    /** 老互通服没有记录基岩版版本：后台从已装的 Geyser 插件补一次，补到就刷新列表 */
    private fun backfillBedrockVersion() {
        if (backfilling) return
        val arr0 = Store.loadServers()
        var need = false
        for (i in 0 until arr0.length()) {
            val o = arr0.optJSONObject(i) ?: continue
            if (o.optString("core") == Cores.CROSSPLAY && o.optString("bedrockVersion").isEmpty()) { need = true; break }
        }
        if (!need) return
        backfilling = true
        Thread {
            try {
                val arr = Store.loadServers()
                var changed = false
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("core") != Cores.CROSSPLAY) continue
                    if (o.optString("bedrockVersion").isNotEmpty()) continue
                    val jar = java.io.File(Store.serverDir(o.optString("id")), "plugins/Geyser-Spigot.jar")
                    val bv = Cores.bedrockVersion(jar) ?: continue
                    o.put("bedrockVersion", bv)
                    Cores.geyserVersion(jar)?.let { o.put("geyserVersion", it) }
                    changed = true
                }
                if (changed) Store.saveServers(arr)
            } catch (_: Exception) {
            } finally { backfilling = false }
        }.start()
    }

    private fun autoExtractContainer() {
        if (!Store.hasRootfs()) {
            if (Ui.needStorageSilent(this)) {
                Thread {
                    try { Container.ensureRootfs(applicationContext) { _ -> } } catch (_: Exception) {}
                }.start()
            }
        }
    }

    private fun installCrashHook() {
        val app = applicationContext
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val f = java.io.File(Store.base, "crash-" + System.currentTimeMillis() + ".txt")
                f.parentFile?.mkdirs()
                f.writeText(java.io.StringWriter().apply {
                    e.printStackTrace(java.io.PrintWriter(this))
                }.toString())
            } catch (_: Exception) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun addPermCard() {
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            val c = Ui.card(this)
            c.setPadding(Ui.px(this, 18), Ui.px(this, 16), Ui.px(this, 18), Ui.px(this, 16))
            c.addView(Ui.text(this, "⚠ 需要文件访问权限", 16f, getColor(R.color.amber_text), true))
            val d = Ui.text(this,
                "用于存放服务器文件（/sdcard/MCFuwuqi）。点下方去系统设置，打开「允许访问所有文件」。", 13f, getColor(R.color.text_sub))
            d.setPadding(0, Ui.px(this, 6), 0, Ui.px(this, 12))
            c.addView(d)
            val btn = Ui.text(this, "去授权", 15f, Color.WHITE, true)
            btn.gravity = Gravity.CENTER
            btn.setBackgroundResource(R.drawable.bg_btn_green)
            btn.setPadding(Ui.px(this, 24), Ui.px(this, 10), Ui.px(this, 24), Ui.px(this, 10))
            btn.setOnClickListener { Ui.needStorage(this); }
            c.addView(btn, Ui.lp(-2, -2))
            body.addView(c, Ui.lp(-1, -2).also { it.bottomMargin = Ui.px(this, 12) })
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9)
        }
    }

    private fun addEmpty() {
        val box = Ui.linear(this, true)
        box.gravity = Gravity.CENTER
        box.setPadding(0, Ui.px(this, 110), 0, Ui.px(this, 110))
        box.addView(Ui.text(this, "还没有服务器", 20f, getColor(R.color.text), true))
        val s = Ui.text(this, "选一个版本，一分钟就能开服", 14f, getColor(R.color.text_sub))
        s.setPadding(0, Ui.px(this, 8), 0, Ui.px(this, 28))
        box.addView(s)
        val btn = Ui.text(this, "＋ 新建服务器", 17f, Color.WHITE, true)
        btn.gravity = Gravity.CENTER
        btn.setBackgroundResource(R.drawable.bg_btn_green)
        btn.setPadding(Ui.px(this, 36), Ui.px(this, 13), Ui.px(this, 36), Ui.px(this, 13))
        btn.setOnClickListener { startActivity(Intent(this, NewServerActivity::class.java)) }
        box.addView(btn, Ui.lp(-2, -2))
        body.addView(box)
    }

    private fun addServerCard(o: JSONObject, running: Boolean) {
        val id = o.optString("id")
        val c = Ui.card(this)
        c.setPadding(Ui.px(this, 18), Ui.px(this, 16), Ui.px(this, 18), Ui.px(this, 16))
        val row = Ui.linear(this, false)
        row.gravity = Gravity.CENTER_VERTICAL
        val nameCol = Ui.linear(this, true)
        nameCol.addView(Ui.text(this, o.optString("name", "服务器"), 17f, getColor(R.color.text), true))
        val isCross = o.optString("core") == Cores.CROSSPLAY
        val bv = o.optString("bedrockVersion")
        val crossTag = if (isCross) " · " + (if (bv.isEmpty()) "基岩 :19132" else "基岩 $bv") else ""
        val dot = if (running) "● 运行中 · :${o.optInt("port", 25565)}$crossTag"
            else "○ ${o.optString("core")} ${o.optString("mcVersion")} · :${o.optInt("port", 25565)}$crossTag"
        val st = Ui.text(this, dot, 13f, if (running) getColor(R.color.green) else getColor(R.color.text_sub))
        st.setPadding(0, Ui.px(this, 4), 0, 0)
        nameCol.addView(st)
        row.addView(nameCol, Ui.lp(0, -2, 1f))
        row.addView(Ui.text(this, if (running) "控制台 ›" else "启动 ›", 15f, getColor(R.color.green), true))
        row.setOnClickListener {
            startActivity(Intent(this, ConsoleActivity::class.java).putExtra("id", id))
        }
        c.addView(row)
        body.addView(c, Ui.lp(-1, -2).also { it.topMargin = Ui.px(this, 12) })
    }

    /** 底部两胶囊：服务端 / 设置；整条背景透明 100% */
    private fun buildBottomBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Color.TRANSPARENT)
        bar.setPadding(Ui.px(this, 8), Ui.px(this, 8), Ui.px(this, 8), Ui.px(this, 10))

        fun tab(label: String, iconRes: Int): LinearLayout {
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            t.gravity = Gravity.CENTER_HORIZONTAL
            val pill = android.widget.FrameLayout(this)
            val iv = ImageView(this)
            iv.setImageResource(iconRes)
            val ip = android.widget.FrameLayout.LayoutParams(Ui.px(this, 26), Ui.px(this, 26))
            ip.gravity = Gravity.CENTER
            pill.addView(iv, ip)
            t.addView(pill, Ui.lp(-2, -2))
            val tv = Ui.text(this, label, 13f, getColor(R.color.text_sub), true)
            tv.gravity = Gravity.CENTER_HORIZONTAL
            tv.setPadding(0, Ui.px(this, 5), 0, 0)
            t.addView(tv, Ui.lp(-2, -2))
            return t
        }

        tabServer = tab("服务端", R.drawable.ic_home)
        tabSet = tab("设置", R.drawable.ic_gear)
        fun add(t: LinearLayout) = bar.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        add(tabServer); add(tabSet)
        tabServer.setOnClickListener { switchTab(TAB_SERVER) }
        tabSet.setOnClickListener { switchTab(TAB_SETTINGS) }
        return bar
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.immersive(this)
    }
}
