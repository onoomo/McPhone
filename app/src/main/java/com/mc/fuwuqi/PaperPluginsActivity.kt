package com.mc.fuwuqi

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.json.JSONObject
import java.io.File

/**
 * 插件管理：只显示当前服务器 plugins/ 目录里真实存在的插件。
 * 开关通过重命名 .jar <-> .jar.disabled 实现启用/禁用（Paper 只加载 .jar），重启服务器生效。
 */
class PaperPluginsActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var listWrap: LinearLayout
    private var preferId = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        preferId = intent?.getStringExtra("id") ?: ""
        val root = Ui.linear(this, true)
        root.setBackgroundColor(getColor(R.color.bg))
        root.addView(Ui.topBar(this, "插件管理", "开启/关闭后重启服务器生效"))
        val scroll = Ui.scroll(this)
        listWrap = Ui.linear(this, true)
        listWrap.setPadding(Ui.px(this, 20), Ui.px(this, 8), Ui.px(this, 20), Ui.px(this, 24))
        scroll.addView(listWrap, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, Ui.lp(-1, 0, 1f))
        setContentView(root)
        Ui.immersive(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        listWrap.removeAllViews()
        if (!Ui.needStorage(this)) return
        val servers = (0 until Store.loadServers().length()).map { Store.loadServers().getJSONObject(it) }
            .filter { Cores.isBukkitLike(it.optString("core")) }
        if (servers.isEmpty()) {
            listWrap.addView(Ui.text(this,
                "还没有可用的 Bukkit 系服务器（Paper / 互通服 / Purpur）。先去创建一个吧。",
                14f, getColor(R.color.text_sub)).also { it.setPadding(0, Ui.px(this, 30), 0, 0) })
            return
        }
        val srv: JSONObject = if (preferId.isNotEmpty())
            servers.firstOrNull { it.optString("id") == preferId } ?: servers[0]
        else servers[0]
        val serverDir = Store.serverDir(srv.optString("id"))
        val plugins = File(serverDir, "plugins")

        // 只列 plugins/ 目录下真实存在的插件文件：*.jar（启用）与 *.jar.disabled（禁用）
        val jars = (plugins.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.lowercase().endsWith(".jar") || it.name.lowercase().endsWith(".jar.disabled")) }
            .sortedBy { displayName(it).lowercase() }

        val sc = sectionCard("本服务器插件开关",
            "当前：${srv.optString("name", "服务器")}｜开启 → 保留 .jar（重启加载）；关闭 → 改名 .jar.disabled（重启后不加载）")
        if (jars.isEmpty()) {
            sc.addView(Ui.text(this,
                "plugins/ 目录里还没有插件。用文件管理器把 .jar 放进去，或用服务器面板安装即可在这里开关。",
                13f, getColor(R.color.text_sub)).also { it.setPadding(0, Ui.px(this, 14), 0, Ui.px(this, 14)) })
        } else {
            jars.forEachIndexed { i, f ->
                val on = f.name.lowercase().endsWith(".jar")
                val row = Ui.linear(this, false)
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, Ui.px(this, 12), 0, Ui.px(this, 12))
                val col = Ui.linear(this, true)
                col.addView(Ui.text(this, displayName(f), 16f, getColor(R.color.text), true))
                val state = Ui.text(this, if (on) "已启用（在 plugins/ 中）" else "已禁用（.jar.disabled）",
                    12f, getColor(R.color.text_sub))
                state.setPadding(0, Ui.px(this, 2), 0, 0)
                col.addView(state)
                row.addView(col, Ui.lp(0, -2, 1f))

                val sw = ConfigActivity.SwitchView(this, on) { checked ->
                    val ok = if (checked) enable(f) else disable(f)
                    if (!ok) Ui.toast(this, "操作失败：无法重命名文件")
                    render()
                }
                sw.layoutParams = Ui.lp(Ui.px(this, 46), Ui.px(this, 26))
                row.addView(sw)
                sc.addView(row)
                if (i < jars.lastIndex) sc.addView(hLine())
            }
        }
        listWrap.addView(sc, cardMargin())
    }

    /** 显示名：去掉 .jar / .jar.disabled 后缀 */
    private fun displayName(f: File): String {
        val n = f.name
        return when {
            n.lowercase().endsWith(".jar.disabled") -> n.substring(0, n.length - ".jar.disabled".length)
            n.lowercase().endsWith(".jar") -> n.substring(0, n.length - ".jar".length)
            else -> n
        }
    }

    /** 禁用：xxx.jar -> xxx.jar.disabled */
    private fun disable(f: File): Boolean =
        try { f.renameTo(File(f.parentFile, "${displayName(f)}.jar.disabled")) } catch (_: Exception) { false }

    /** 启用：xxx.jar.disabled -> xxx.jar */
    private fun enable(f: File): Boolean =
        try { f.renameTo(File(f.parentFile, "${displayName(f)}.jar")) } catch (_: Exception) { false }

    private fun sectionCard(title: String, sub: String): LinearLayout {
        listWrap.addView(Ui.text(this, title, 17f, getColor(R.color.text_sub), true).also {
            it.setPadding(Ui.px(this, 6), Ui.px(this, 20), 0, Ui.px(this, 6)) })
        val card = Ui.card(this)
        card.setPadding(Ui.px(this, 18), Ui.px(this, 8), Ui.px(this, 18), Ui.px(this, 8))
        card.addView(Ui.text(this, sub, 12.5f, getColor(R.color.text_sub)).also {
            it.setPadding(0, Ui.px(this, 8), 0, Ui.px(this, 4)) })
        return card
    }

    private fun cardMargin(): ViewGroup.LayoutParams =
        Ui.lp(-1, -2).also { (it as LinearLayout.LayoutParams).topMargin = Ui.px(this, 4) }

    private fun hLine(): View {
        val v = View(this)
        v.setBackgroundColor(getColor(R.color.line))
        v.layoutParams = Ui.lp(-1, Ui.px(this, 1))
        return v
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.immersive(this)
    }
}
