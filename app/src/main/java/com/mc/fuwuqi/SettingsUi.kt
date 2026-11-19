package com.mc.fuwuqi

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 设置页内容：供 SettingsActivity 与 MainActivity（内嵌直接显示）共用。
 * 所有上下文通过入参 a / host 传入，避免绑定具体 Activity。
 */
object SettingsUi {
    private var busy = false
    private val progressViews = mutableListOf<TextView>()
    private val rowProgs = HashMap<Int, TextView>()

    fun render(host: LinearLayout, a: Activity) {
        host.removeAllViews()
        progressViews.clear()
        rowProgs.clear()
        if (!Ui.needStorage(a)) return

        // Linux 容器
        host.addView(section(a, "Linux 容器"))
        val cc = Ui.card(a)
        cc.setPadding(Ui.px(a, 18), Ui.px(a, 14), Ui.px(a, 18), Ui.px(a, 16))
        cc.addView(urow(a, "✓  内置容器 · Debian glibc 精简版",
            if (Store.hasRootfs()) "已就绪" else "待解压", Store.hasRootfs()))
        cc.addView(urow(a, "✓  proot 运行时（随 APK 内置）",
            if (Container.hasProot(a)) "已内置" else "未就绪", Container.hasProot(a)))
        val exp = Ui.text(a,
            "Java 直接跑在这个容器里，不依赖 Termux 或任何 App。容器用的是 Debian 的 glibc 用户态，和电脑上开服的环境一致。",
            13f, a.getColor(R.color.text_sub))
        exp.setPadding(0, Ui.px(a, 8), 0, 0)
        cc.addView(exp)
        // 容器已就绪时不再放空按钮/空进度条，避免卡片底部留大片空白
        if (!Store.hasRootfs()) {
            val act = Ui.text(a, "解压内置容器（无需下载）", 15f, a.getColor(R.color.green), true)
            act.setPadding(0, Ui.px(a, 12), 0, 0)
            act.setOnClickListener {
                if (busy) { Ui.toast(a, "有任务进行中，请稍候"); return@setOnClickListener }
                doDownload(a) {
                    if (it.startsWith("ERR")) Ui.toast(a, it) else Ui.toast(a, "容器已就绪")
                    render(host, a)
                }
            }
            cc.addView(act)
            val prog = Ui.text(a, "", 12f, a.getColor(R.color.text_sub))
            cc.addView(prog)
            progressViews.add(prog)
        }
        host.addView(cc, Ui.lp(-1, -2).also { it.topMargin = Ui.px(a, 4) })

        // Java 运行时
        host.addView(section(a, "Java 运行时"))
        val jc = Ui.card(a)
        jc.setPadding(Ui.px(a, 18), Ui.px(a, 14), Ui.px(a, 18), Ui.px(a, 16))
        jc.addView(Ui.text(a, "内置 5 个 LTS 版本，创建服务器时按 MC 版本自动匹配。按需下载，用不到的不占空间。", 13f, a.getColor(R.color.text_sub)))
        for (v in intArrayOf(8, 11, 17, 21, 25)) {
            val installed = Store.hasJdk(v)
            val row = Ui.linear(a, false)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, Ui.px(a, 14), 0, Ui.px(a, 14))
            val col = Ui.linear(a, true)
            col.addView(Ui.text(a, "Java $v", 16f, a.getColor(R.color.text), true))
            val d = Ui.text(a, Container.jdkDesc[v] ?: "", 12f, a.getColor(R.color.text_sub))
            d.setPadding(0, Ui.px(a, 2), 0, 0)
            col.addView(d)
            row.addView(col, Ui.lp(0, -2, 1f))
            val btn = Ui.text(a, if (installed) "已安装 ✓" else "安装", 15f,
                if (installed) a.getColor(R.color.text_sub) else a.getColor(R.color.green), true)
            btn.setOnClickListener {
                if (installed) return@setOnClickListener
                if (busy) { Ui.toast(a, "有任务进行中，请稍候"); return@setOnClickListener }
                doJdk(a, v) {
                    if (it.startsWith("ERR")) Ui.toast(a, it)
                    else Ui.toast(a, "Java $v 已就绪")
                    render(host, a)
                }
            }
            row.addView(btn)
            jc.addView(row)
            val rp = Ui.text(a, "", 12f, a.getColor(R.color.green))
            rp.setPadding(0, 0, 0, Ui.px(a, 4))
            jc.addView(rp)
            rowProgs[v] = rp
            if (v != 25) {
                val line = View(a)
                line.setBackgroundColor(a.getColor(R.color.line))
                jc.addView(line, Ui.lp(-1, Ui.px(a, 1)))
            }
        }
        val foot = Ui.linear(a, false)
        foot.gravity = Gravity.CENTER_VERTICAL
        val refresh = Ui.text(a, "刷新状态", 14f, a.getColor(R.color.green), true)
        refresh.setOnClickListener { render(host, a) }
        foot.addView(refresh, Ui.lp(0, -2, 1f))
        foot.addView(Ui.text(a, "每个版本约 45MB", 12f, a.getColor(R.color.text_sub)))
        foot.setPadding(0, Ui.px(a, 6), 0, 0)
        jc.addView(foot)
        host.addView(jc, Ui.lp(-1, -2).also { it.topMargin = Ui.px(a, 4) })

        // 关于
        host.addView(section(a, "关于"))
        val ab = Ui.card(a)
        ab.setPadding(Ui.px(a, 18), Ui.px(a, 14), Ui.px(a, 18), Ui.px(a, 16))
        val ver = try { a.packageManager.getPackageInfo(a.packageName, 0) } catch (e: Exception) { null }
        val code = ver?.let {
            if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode
            else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: 0L
        ab.addView(Ui.text(a, "MC开服 v${ver?.versionName ?: "?"} (build $code)", 15f, a.getColor(R.color.text), true))
        ab.addView(Ui.text(a,
            "容器方案：proot（ptrace 路径重定向）+ Debian glibc 精简 rootfs + Temurin JRE。服务器文件都在 ${Store.serversDir.absolutePath}/ ，可以用文件管理器直接改配置、塞插件。",
            12f, a.getColor(R.color.text_sub)).also { it.setPadding(0, Ui.px(a, 6), 0, 0) })
        host.addView(ab, Ui.lp(-1, -2).also { it.topMargin = Ui.px(a, 4) })

        // 明暗模式
        host.addView(section(a, "外观"))
        val darkCard = Ui.card(a)
        darkCard.setPadding(Ui.px(a, 18), Ui.px(a, 14), Ui.px(a, 18), Ui.px(a, 16))
        val trow = Ui.linear(a, false)
        trow.gravity = Gravity.CENTER_VERTICAL
        trow.addView(Ui.text(a, "深色模式（纯黑）", 15f, a.getColor(R.color.text), true), Ui.lp(0, -2, 1f))
        val sw = ConfigActivity.SwitchView(a, a.getSharedPreferences("app", 0).getBoolean("dark", false)) { on ->
            a.getSharedPreferences("app", 0).edit().putBoolean("dark", on).apply()
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                if (on) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            a.recreate()
        }
        sw.layoutParams = Ui.lp(Ui.px(a, 46), Ui.px(a, 26))
        trow.addView(sw)
        darkCard.addView(trow)
        host.addView(darkCard, Ui.lp(-1, -2).also { it.topMargin = Ui.px(a, 4) })

        // 高级
        host.addView(section(a, "高级"))
        val ac = Ui.card(a)
        ac.setPadding(Ui.px(a, 18), Ui.px(a, 14), Ui.px(a, 18), Ui.px(a, 16))
        ac.addView(Ui.text(a, "JVM 启动参数", 16f, a.getColor(R.color.text), true))
        val e = EditText(a)
        val sp = a.getSharedPreferences("app", 0)
        e.setText(sp.getString("jvm_flags", ServerManager.DEFAULT_FLAGS))
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f)
        e.setTextColor(Color.parseColor("#3A3E3C"))
        e.typeface = android.graphics.Typeface.MONOSPACE
        e.setBackgroundResource(R.drawable.bg_console)
        e.setPadding(Ui.px(a, 14), Ui.px(a, 10), Ui.px(a, 14), Ui.px(a, 10))
        e.minLines = 3
        e.gravity = Gravity.TOP
        val ep = Ui.lp(-1, -2); ep.topMargin = Ui.px(a, 10)
        ac.addView(e, ep)
        ac.addView(Ui.text(a, "默认是 Paper 推荐的 G1GC 参数，针对小内存做过调整。不懂别改。", 12f, a.getColor(R.color.text_sub)).also {
            it.setPadding(0, Ui.px(a, 8), 0, 0) })
        val saveFlag = Ui.text(a, "保存参数", 15f, a.getColor(R.color.green), true)
        saveFlag.setPadding(0, Ui.px(a, 10), 0, 0)
        saveFlag.setOnClickListener {
            sp.edit().putString("jvm_flags", e.text?.toString()?.trim() ?: "").apply()
            Ui.toast(a, "已保存")
        }
        ac.addView(saveFlag)
        host.addView(ac, Ui.lp(-1, -2).also { it.topMargin = Ui.px(a, 4) })

        // 后台保活
        host.addView(section(a, "后台保活"))
        val pc = Ui.card(a)
        pc.setPadding(Ui.px(a, 18), Ui.px(a, 14), Ui.px(a, 18), Ui.px(a, 16))
        pc.addView(Ui.text(a, "电池优化豁免", 16f, a.getColor(R.color.text), true))
        pc.addView(Ui.text(a, "不开这项，息屏约 10 分钟后系统会进入 Doze 掐断网络，客户端就报「连接已丢失 / Connection reset」。开服 App 建议必开。", 12f, a.getColor(R.color.text_sub)).also {
            it.setPadding(0, Ui.px(a, 4), 0, 0) })
        val batBtn = Ui.text(a, "申请忽略电池优化", 15f, a.getColor(R.color.green), true)
        batBtn.setPadding(0, Ui.px(a, 10), 0, 0)
        val pm = a.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        fun refreshBat() {
            val ign = pm.isIgnoringBatteryOptimizations(a.packageName)
            batBtn.text = if (ign) "✓ 已忽略电池优化" else "申请忽略电池优化"
        }
        refreshBat()
        batBtn.setOnClickListener {
            if (pm.isIgnoringBatteryOptimizations(a.packageName)) {
                Ui.toast(a, "已经在白名单里了")
            } else {
                try {
                    a.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${a.packageName}")))
                } catch (e: Exception) {
                    try { a.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
                }
            }
        }
        pc.addView(batBtn)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val notifOk = a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val nBtn = Ui.text(a,
                if (notifOk) "✓ 通知已开启（可显示运行状态与停止按钮）" else "开启通知（显示运行状态与停止按钮）",
                14f, if (notifOk) a.getColor(R.color.text_sub) else a.getColor(R.color.green), true)
            nBtn.setPadding(0, Ui.px(a, 12), 0, 0)
            nBtn.setOnClickListener {
                if (!notifOk) a.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 21)
                else Ui.toast(a, "已开启")
            }
            pc.addView(nBtn)
        }
        val bgBtn = Ui.text(a, "允许后台运行 / 自启动（国产 ROM 必开）", 15f, a.getColor(R.color.green), true)
        bgBtn.setPadding(0, Ui.px(a, 12), 0, 0)
        bgBtn.setOnClickListener { openAutoStartSettings(a) }
        pc.addView(bgBtn)
        val bgDesc = Ui.text(a, "小米/红米、华为/荣耀、OPPO/一加、vivo/iQOO 等系统会额外清理后台，需在「自启动/后台运行」里放行本应用。", 12f, a.getColor(R.color.text_sub))
        bgDesc.setPadding(0, Ui.px(a, 6), 0, 0)
        pc.addView(bgDesc)
        host.addView(pc, Ui.lp(-1, -2).also { it.topMargin = Ui.px(a, 4) })

        // 工具
        host.addView(section(a, "工具"))
        val tc = Ui.card(a)
        tc.setPadding(Ui.px(a, 18), Ui.px(a, 14), Ui.px(a, 18), Ui.px(a, 16))
        tc.addView(Ui.text(a, "执行指令", 16f, a.getColor(R.color.text), true))
        tc.addView(Ui.text(a, "在本机执行 shell 命令并查看输出（排障用）", 13f, a.getColor(R.color.text_sub)).also {
            it.setPadding(0, Ui.px(a, 4), 0, Ui.px(a, 8)) })
        tc.setOnClickListener { a.startActivity(Intent(a, CmdActivity::class.java)) }
        host.addView(tc, Ui.lp(-1, -2).also { it.topMargin = Ui.px(a, 4) })
    }

    private fun section(a: Activity, title: String): View {
        val t = Ui.text(a, title, 17f, a.getColor(R.color.text_sub), true)
        t.setPadding(Ui.px(a, 6), Ui.px(a, 20), 0, Ui.px(a, 6))
        return t
    }

    private fun urow(a: Activity, left: String, right: String, ok: Boolean): View {
        val row = Ui.linear(a, false)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.px(a, 4), 0, Ui.px(a, 4))
        row.addView(Ui.text(a, left, 15f, a.getColor(R.color.text), true), Ui.lp(0, -2, 1f))
        row.addView(Ui.text(a, right, 14f, a.getColor(if (ok) R.color.green else R.color.danger), true))
        return row
    }

    private fun doJdk(a: Activity, v: Int, done: (String) -> Unit) {
        busy = true
        Thread {
            var r = "OK"
            try {
                if (!Store.hasRootfs()) {
                    Container.ensureRootfs(a.applicationContext) { s -> a.runOnUiThread { rowProgs[v]?.text = s } }
                }
                Container.downloadJdk(v) { s -> a.runOnUiThread { rowProgs[v]?.text = s; progressViews.forEach { it.text = "" } } }
                if (!Store.hasJdk(v)) r = "ERR:安装后校验失败"
            } catch (e: Exception) {
                r = "ERR:" + (e.message ?: e.toString())
            } finally { busy = false }
            a.runOnUiThread { rowProgs[v]?.text = ""; done(r) }
        }.start()
    }

    private fun doDownload(a: Activity, done: (String) -> Unit) {
        busy = true
        Thread {
            try {
                if (!Store.hasRootfs()) {
                    Container.ensureRootfs(a.applicationContext) { s -> a.runOnUiThread { progressViews.forEach { it.text = s } } }
                }
                a.runOnUiThread { done("OK") }
            } catch (e: Exception) {
                a.runOnUiThread { done("ERR:${e.message}") }
            } finally { busy = false }
        }.start()
    }

    private fun openAutoStartSettings(a: Activity) {
        val m = android.os.Build.MANUFACTURER.lowercase()
        val targets = when {
            m.contains("xiaomi") || m.contains("redmi") -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
            m.contains("huawei") || m.contains("honor") -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity")
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity")
            m.contains("vivo") || m.contains("iqoo") -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            else -> emptyList()
        }
        for ((pkg, cls) in targets) {
            try {
                a.startActivity(Intent().setComponent(android.content.ComponentName(pkg, cls)))
                return
            } catch (_: Exception) {}
        }
        try {
            a.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${a.packageName}")))
        } catch (_: Exception) {}
    }
}
