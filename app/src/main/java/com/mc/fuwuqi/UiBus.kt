package com.mc.fuwuqi

/** 参照物 StateFlow 的等价物：任何状态变更 post，订阅页自动刷新 */
object UiBus {
    // 以 owner 为键：同一个 owner 重复订阅只保留一个，且能精确注销，
    // 避免 lambda 实例不等导致 unsubscribe 失效、页面反复进出后累积泄漏。
    private val listeners = LinkedHashMap<Any, () -> Unit>()

    fun subscribe(owner: Any, cb: () -> Unit) { synchronized(listeners) { listeners[owner] = cb } }
    fun unsubscribe(owner: Any) { synchronized(listeners) { listeners.remove(owner) } }

    @Volatile private var lastPost = 0L
    fun post() {
        // 300ms 抖动合并，避免日志刷屏拖垮 UI
        val now = System.currentTimeMillis()
        if (now - lastPost < 300) return
        lastPost = now
        val snapshot: List<() -> Unit> = synchronized(listeners) { listeners.values.toList() }
        snapshot.forEach {
            try { it() } catch (_: Exception) {}
        }
    }
}
