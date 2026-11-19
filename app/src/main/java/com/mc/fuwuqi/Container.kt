package com.mc.fuwuqi

import android.content.Context
import java.io.File
import org.json.JSONArray

/** 容器层：proot（native libs，随 APK 内置）+ Debian rootfs（内置 assets）+ JRE 按需下载 */
object Container {

    /** JRE 主源：本仓库 javalts 目录（xz 压缩，按需下载）；Adoptium 仅作兜底 */
    const val ADOPTIUM = "https://api.adoptium.net/v3/binary/latest/%d/ga/linux/aarch64/jre/hotspot/normal/eclipse"
    const val JAVALTS = "https://raw.githubusercontent.com/onoomo/McPhone/main/javalts/"

    /** 仓库 javalts 里的 5 个 JRE 文件名（tar.xz） */
    val jreNames = mapOf(
        8 to "OpenJDK8U-jre_aarch64_linux_hotspot_8u512b09.tar.xz",
        11 to "OpenJDK11U-jre_aarch64_linux_hotspot_11.0.33_10.tar.xz",
        17 to "OpenJDK17U-jre_aarch64_linux_hotspot_17.0.21_9.tar.xz",
        21 to "OpenJDK21U-jre_aarch64_linux_hotspot_21.0.13_9.tar.xz",
        25 to "OpenJDK25U-jre_aarch64_linux_hotspot_25.0.5_8.tar.xz")

    val jdkDesc = mapOf(
        8 to "支持 1.7 – 1.16.5",
        11 to "部分旧版整合包 / 特殊端",
        17 to "支持 1.17 – 1.20.4",
        21 to "支持 1.20.5 – 1.21.x",
        25 to "支持 26.x 及以后"
    )

    fun javaForMc(mcVersion: String): Int {
        val p = mcVersion.split('.')
        val major = p.getOrNull(1)?.toIntOrNull() ?: 0
        val minor = p.getOrNull(2)?.toIntOrNull() ?: 0
        return when {
            mcVersion.startsWith("26.") -> 25
            major >= 21 || (major == 20 && minor >= 5) -> 21
            major >= 17 -> 17
            else -> 8
        }
    }

    /** proot 本体（随 APK 安装，位于 nativeLibraryDir） */
    fun prootBin(ctx: Context): File {
        val nd = File(ctx.applicationInfo.nativeLibraryDir)
        return File(nd, "libproot.so")
    }

    fun hasProot(ctx: Context): Boolean = prootBin(ctx).isFile

    /** 解压内置 rootfs（tar.gz，无需网络） */
    fun ensureRootfs(ctx: Context, cb: (String) -> Unit): Boolean {
        if (Store.hasRootfs()) return true
        cb("解压内置容器 …")
        val tmp = File(Store.runDir, "rootfs-a64.bin")
        if (!tmp.isFile) {
            ctx.assets.open("rootfs/rootfs-a64.bin").use { ins ->
                tmp.parentFile?.mkdirs()
                tmp.outputStream().use { ins.copyTo(it) }
            }
        }
        cb("解包容器 rootfs …")
        Dl.extractTar(tmp, Store.rootfsDir(), { n -> cb("解包容器 rootfs … $n 个文件") })
        tmp.delete()
        File(Store.rootfsDir(), ".minehost-rootfs").writeText("1")
        return Store.hasRootfs()
    }

    fun downloadJdk(v: Int, cb: (String) -> Unit): Boolean {
        if (Store.hasJdk(v)) return true
        // 旧版本装在 runDir/jdk<v>（目录改名前）：直接迁移进容器，避免重新下载
        val legacy = File(Store.runDir, "jdk$v")
        if (File(legacy, "bin/java").isFile) {
            cb("迁移已下载的 Java $v …")
            val dst = Store.jdkDir(v)
            dst.parentFile?.mkdirs(); dst.deleteRecursively()
            if (!legacy.renameTo(dst)) { legacy.copyRecursively(dst, true); legacy.deleteRecursively() }
            File(dst, "bin/java").setExecutable(true)
            if (Store.hasJdk(v)) return true
        }
        val tmp = File(Store.runDir, "jdk$v.tar.xz")
        cb("下载 Java $v …")
        // 主源：本仓库 javalts（Dl.download 内部自带 原址 → gh-proxy → ghproxy.net 兜底）；只下所选版本
        val name = jreNames[v] ?: throw RuntimeException("不支持的 Java 版本 $v")
        try {
            Dl.download(JAVALTS + name, tmp) { cur, tot ->
                cb("下载 Java $v ${fmt(cur)}" + (if (tot > 0) " / ${fmt(tot)}" else ""))
            }
        } catch (e: Exception) {
            // 兜底：Adoptium 官方（tar.gz，解包按魔数嗅探兼容）
            cb("仓库 JRE 不可用，尝试 Adoptium …")
            try {
                Dl.download(String.format(ADOPTIUM, v), tmp) { cur, tot ->
                    cb("下载 Java $v ${fmt(cur)}" + (if (tot > 0) " / ${fmt(tot)}" else ""))
                }
            } catch (e2: Exception) {
                Dl.download("https://gh-proxy.com/" + String.format(ADOPTIUM, v), tmp) { cur, tot ->
                    cb("下载 Java $v ${fmt(cur)}" + (if (tot > 0) " / ${fmt(tot)}" else ""))
                }
            }
        }
        cb("解包 Java $v …")
        // 参照物同款：strip 顶层目录直接解进 rootfs/opt/jdk-<v>
        val dst = Store.jdkDir(v)
        dst.deleteRecursively(); dst.mkdirs()
        Dl.extractTar(tmp, dst, { n -> cb("解包 Java $v … $n 个文件") }, stripComponents = 1)
        if (!Store.hasJdk(v)) {
            // 兜底：结构异常时全量再扫一次
            var found: File? = null
            for (l1 in dst.listFiles() ?: emptyArray()) {
                if (File(l1, "bin/java").isFile) { found = l1; break }
                for (l2 in l1.listFiles() ?: emptyArray()) if (File(l2, "bin/java").isFile) { found = l2; break }
                if (found != null) break
            }
            if (found != null && found != dst) {
                val tmpD = File(dst.parentFile, "jdk${v}_m")
                found.renameTo(tmpD); dst.deleteRecursively(); tmpD.renameTo(dst)
            }
        }
        if (!Store.hasJdk(v)) {
            dst.deleteRecursively(); tmp.delete()
            throw RuntimeException("解包后未找到 bin/java（包可能损坏）")
        }
        File(dst, "bin/java").setExecutable(true)
        tmp.delete()
        return true

    }

    fun fmt(b: Long): String = if (b >= 1_000_000) "${"%.1f".format(b / 1_000_000f)}MB" else "${b / 1024}KB"

    /** 设置页状态：容器 + 5 个 JRE */
    fun statusJson(ctx: Context): String {
        val o = org.json.JSONObject()
        o.put("proot", hasProot(ctx))
        o.put("rootfs", Store.hasRootfs())
        val j = JSONArray()
        for (v in intArrayOf(8, 11, 17, 21, 25)) {
            val jo = org.json.JSONObject()
            jo.put("v", v)
            jo.put("installed", Store.hasJdk(v))
            j.put(jo)
        }
        o.put("jdks", j)
        return o.toString()
    }
}
