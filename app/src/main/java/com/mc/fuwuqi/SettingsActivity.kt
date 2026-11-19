package com.mc.fuwuqi

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout

/** 设置页：容器状态 / Java 运行时 / JVM 参数 / 关于（内容与主页内嵌共用 SettingsUi） */
class SettingsActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var listWrap: LinearLayout

    override fun onResume() {
        super.onResume()
        render()
        UiBus.subscribe(this) { runOnUiThread { render() } }
    }

    override fun onPause() {
        super.onPause()
        UiBus.unsubscribe(this)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = Ui.linear(this, true)
        root.setBackgroundColor(getColor(R.color.bg))
        root.addView(Ui.topBar(this, "设置", "容器 · 运行时 · 高级"))

        listWrap = Ui.linear(this, true)
        listWrap.setPadding(Ui.px(this, 20), Ui.px(this, 4), Ui.px(this, 20), Ui.px(this, 24))
        val scroll = Ui.scroll(this)
        scroll.addView(listWrap, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, Ui.lp(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() = SettingsUi.render(listWrap, this)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.immersive(this)
    }
}
