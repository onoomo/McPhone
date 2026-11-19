package com.mc.fuwuqi

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.compressors.CompressorStreamFactory

/** HTTP 下载 + tar.xz / tar.gz 解包 */
object Dl {

    private const val CHUNKS = 15               // 分块数（并发线程数）
    private const val MIN_PARALLEL = 2L * 1024 * 1024  // 小于 2MB 不分包

    /** 分块文件名带线程数前缀：线程数变化时旧块边界失效，只复用同线程数的块 */
    private fun blkFile(dest: File, i: Int) = File(dest.absolutePath + ".b${CHUNKS}_$i")

    /** 下载到 destFile，cb(current,total) 节流回调，total 可为 -1 */
    fun download(urlStr: String, destFile: File, cb: (Long, Long) -> Unit = { _, _ -> }): Boolean {
        destFile.parentFile?.mkdirs()
        DownloadService.start(App.inst, "下载 ${destFile.name}")
        DownloadService.progress(0, -1)
        try {
            // 目标文件只有在"完整落地"后才会出现（分块合并写入 .merging，校验通过才 rename），
            // 因此存在即视为已下载完成，正常复用不产生额外网络探测。
            if (destFile.exists() && destFile.length() > 0) return true
            val sources = listOf(urlStr, "https://gh-proxy.com/$urlStr", "https://ghproxy.net/$urlStr")
            val total = probeTotal(sources)
            if (total > MIN_PARALLEL) return downloadParallel(sources, destFile, total, cb)

            // 不支持 Range / 小文件：单线程多源兜底（原有逻辑）
            var lastErr: Exception? = null
            for (u in sources) {
                try {
                    downloadOne(u, destFile, cb)
                    if (destFile.length() > 0) return true
                } catch (e: Exception) { lastErr = e }
            }
            destFile.delete()
            lastErr?.let { throw it }
            return false
        } finally {
            DownloadService.stop(App.inst)
        }
    }

    /** 探测文件总字节数：依次试各源的 Range: bytes=0-0，读 Content-Range 的 /total */
    private fun probeTotal(sources: List<String>): Long {
        for (u in sources) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "MCFuwuqi/1.0")
                conn.setRequestProperty("Range", "bytes=0-0")
                if (conn.responseCode == 206) {
                    val cr = conn.getHeaderField("Content-Range") ?: ""
                    val total = cr.substringAfter('/', "").trim().toLongOrNull() ?: -1L
                    if (total > 0) return total
                }
            } catch (_: Exception) {
            } finally { conn?.disconnect() }
        }
        return -1L
    }

    /** 分块并行下载：CHUNKS 个线程各下一块，块内多源兜底，全部完成后顺序合并 */
    private fun downloadParallel(
        sources: List<String>, destFile: File, total: Long, cb: (Long, Long) -> Unit
    ): Boolean {
        val n = CHUNKS
        val blockSize = (total + n - 1) / n
        // 只清理"线程数前缀不匹配"的旧块；同线程数的断点保留以续传，避免每次都从头下。
        // 兼容历史命名 .blkN（8 线程版）与 .b<线程数>_N（本版），其余文件一律不碰。
        val keepPrefix = destFile.name + ".b${CHUNKS}_"
        val blockHead = destFile.name + ".b"
        val legacyHead = destFile.name + ".blk"
        destFile.parentFile?.listFiles()?.forEach { f ->
            val isBlock = f.name.startsWith(blockHead) || f.name.startsWith(legacyHead)
            if (isBlock && !f.name.startsWith(keepPrefix)) f.delete()
        }
        // 上次合并可能留下残缺的临时文件，先清掉
        File(destFile.absolutePath + ".merging").delete()
        val sum = AtomicLong(0)
        val firstErr = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(n)
        val pool = Executors.newFixedThreadPool(n)
        val cbLock = Any()
        var lastCb = 0L
        var lastCbTime = 0L

        for (i in 0 until n) {
            val start = blockSize * i
            if (start >= total) { latch.countDown(); continue }
            val end = minOf(start + blockSize - 1, total - 1)
            val part = blkFile(destFile, i)
            val initial = if (part.isFile) minOf(part.length(), end - start + 1) else 0L
            sum.addAndGet(initial)

            pool.execute {
                try {
                    downloadBlock(i, start, end, sources, part, sum, cbLock) { nowDone ->
                        val now = System.currentTimeMillis()
                        synchronized(cbLock) {
                            if (nowDone - lastCb >= 200_000L || now - lastCbTime >= 300L) {
                                cb(nowDone, total)
                                DownloadService.progress(nowDone, total)
                                lastCb = nowDone
                                lastCbTime = now
                            }
                        }
                    }
                } catch (e: Throwable) {
                    firstErr.compareAndSet(null, e)
                } finally {
                    latch.countDown()
                }
            }
        }

        try {
            latch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            firstErr.compareAndSet(null, e)
        } finally {
            pool.shutdownNow()
        }

        firstErr.get()?.let { err ->
            for (i in 0 until n) blkFile(destFile, i).delete()
            throw RuntimeException("分块下载失败", err)
        }

        // 顺序合并所有块到"临时文件"，校验通过后再原子落地到 destFile。
        // 这样任何中途异常都不会在 destFile 留下残缺内容被误判为已完成。
        val merging = File(destFile.absolutePath + ".merging")
        val out = RandomAccessFile(merging, "rw")
        try {
            out.setLength(0)
            val buf = ByteArray(256 * 1024)
            for (i in 0 until n) {
                val start = blockSize * i
                if (start >= total) break
                val part = blkFile(destFile, i)
                if (!part.isFile) throw RuntimeException("缺少分块 $i")
                part.inputStream().use { ins ->
                    while (true) {
                        val c = ins.read(buf)
                        if (c < 0) break
                        out.write(buf, 0, c)
                    }
                }
            }
        } finally {
            out.close()
        }
        if (merging.length() != total) {
            merging.delete()
            throw RuntimeException("合并后大小不符: ${merging.length()} != $total")
        }
        // 校验通过，落地：删旧目标 + 重命名（同目录内 rename 为原子操作）
        if (destFile.exists()) destFile.delete()
        if (!merging.renameTo(destFile)) {
            merging.delete()
            throw RuntimeException("无法写入目标文件: ${destFile.name}")
        }
        // 合并成功后才删除各分块，保留到确认落地为止
        for (i in 0 until n) blkFile(destFile, i).delete()
        cb(total, total)
        DownloadService.progress(total, total)
        return true
    }

    /** 下载单个分块；块 i 从轮转源开始，失败依次切下一个源，块内可断点续传 */
    private fun downloadBlock(
        index: Int, start: Long, end: Long, sources: List<String>, part: File,
        sum: AtomicLong, cbLock: Any, onProgress: (Long) -> Unit
    ) {
        var pos = start + (if (part.isFile) part.length() else 0L)
        if (pos > end) return
        val rot = index % sources.size
        val ordered = sources.drop(rot) + sources.take(rot)
        var lastErr: Exception? = null
        for (u in ordered) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 45000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "MCFuwuqi/1.0")
                conn.setRequestProperty("Range", "bytes=$pos-$end")
                if (conn.responseCode != 206) throw RuntimeException("块 $index HTTP ${conn.responseCode} (源不支持 Range)")
                conn.inputStream.use { ins ->
                    val raf = RandomAccessFile(part, "rw")
                    raf.seek(pos - start)
                    try {
                        val buf = ByteArray(64 * 1024)
                        while (pos <= end) {
                            val want = minOf(buf.size, (end - pos + 1).toInt())
                            val c = ins.read(buf, 0, want)
                            if (c < 0) break
                            raf.write(buf, 0, c)
                            pos += c
                            onProgress(sum.addAndGet(c.toLong()))
                        }
                    } finally { raf.close() }
                }
                conn.disconnect()
                if (pos > end) return
            } catch (e: Exception) {
                lastErr = e
                conn?.disconnect()
            }
        }
        throw RuntimeException("块 $index 下载失败 (pos=$pos, end=$end)", lastErr)
    }

    /** 单源整文件下载（含断点续传），作为不支持 Range 时的兜底 */
    private fun downloadOne(urlStr: String, destFile: File, cb: (Long, Long) -> Unit) {
        val tmp = File(destFile.absolutePath + "." + Integer.toHexString(urlStr.hashCode()) + ".part")
        var start = if (tmp.isFile) tmp.length() else 0L
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "MCFuwuqi/1.0")
        if (start > 0) conn.setRequestProperty("Range", "bytes=$start-")
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) { conn.disconnect(); throw RuntimeException("HTTP $code $urlStr") }
        var total = conn.contentLengthLong
        if (code == 200 && start > 0) {
            start = 0L
            total = conn.contentLengthLong
        } else if (code == 206) {
            total += start
        }
        conn.inputStream.use { ins ->
            val raf = RandomAccessFile(tmp, "rw")
            raf.seek(start)
            try {
                val buf = ByteArray(64 * 1024)
                var got = start; var since = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    got += n; since += n
                    if (since > 200_000) { since = 0; cb(got, total); DownloadService.progress(got, total) }
                }
            } finally { raf.close() }
        }
        conn.disconnect()
        if (tmp.length() == 0L) throw RuntimeException("empty download")
        tmp.renameTo(destFile)
    }

    fun fetchText(urlStr: String): String {
        val urls = listOf(urlStr, "https://gh-proxy.com/$urlStr")
        var lastErr: Exception? = null
        for (u in urls) {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "MCFuwuqi/1.0")
                if (conn.responseCode in 200..299) {
                    val s = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    return s
                }
                conn.disconnect()
            } catch (e: Exception) { lastErr = e }
        }
        throw lastErr ?: RuntimeException("fetch failed: $urlStr")
    }

    /** 解 tar.xz / tar.gz 到 outDir（魔数嗅探压缩格式，跳过绝对路径越界；stripComponents 去掉顶层 n 级目录） */
    fun extractTar(archive: File, outDir: File, cb: (Int) -> Unit = {}, stripComponents: Int = 0) {
        outDir.mkdirs()
        val raw = archive.inputStream().buffered()
        raw.mark(4)
        val b0 = raw.read(); val b1 = raw.read(); val b2 = raw.read(); val b3 = raw.read()
        raw.reset()
        val compressor = when {
            b0 == 0x1F && b1 == 0x8B -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.GZIP, raw)
            b0 == 0xFD && b1 == 0x37 && b2 == 0x7A && b3 == 0x58 -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.XZ, raw)
            else -> null
        }
        val stream = compressor ?: raw
        val tar = ArchiveStreamFactory().createArchiveInputStream("tar", stream) as TarArchiveInputStream
        var count = 0
        while (true) {
            val e = tar.nextEntry ?: break
            var rel = e.name.trim().removePrefix("./")
            if (stripComponents > 0) {
                val parts = rel.split('/').filter { it.isNotEmpty() }
                if (parts.size <= stripComponents) continue
                rel = parts.drop(stripComponents).joinToString("/")
            }
            if (rel.isEmpty() || rel.contains("..")) continue
            val out = File(outDir, rel.removePrefix("/"))
            if (!out.canonicalPath.startsWith(outDir.canonicalPath)) continue
            if (e.isDirectory) { out.mkdirs(); continue }
            out.parentFile?.mkdirs()
            if (e.isSymbolicLink) {
                try {
                    out.delete()
                    android.system.Os.symlink(e.linkName, out.absolutePath)
                } catch (ex: Exception) {
                    try {
                        val t = File(outDir, e.linkName.removePrefix("/"))
                        if (t.isFile) t.copyTo(out, true)
                    } catch (_: Exception) {}
                }
                continue
            }
            FileOutputStream(out).use { tar.copyTo(it) }
            try {
                val mode = e.mode and 0b111_111_111
                android.system.Os.chmod(out.absolutePath, mode)
            } catch (_: Exception) {}
            count++
            if (count % 200 == 0) cb(count)
        }
        tar.close()
        (stream as? java.io.Closeable)?.close()
        if (compressor != null) raw.close() else raw.close()
    }
}
