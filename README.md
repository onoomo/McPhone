# McPhone
手机Mc服务器

> 本项目为 MC_fuwuqi 的复刻升级版（Kotlin 2.4.20 + Compose Compiler 2.4.20 + AGP 9.5 + Gradle 9.9.1）。

## 目录结构

- `app/` — Android 项目源码（MainActivity、设置页、容器、插件管理等）
- `plugins/` — 插件资源
- `javalts/` — 5 个 Java LTS (aarch64) JRE 运行时压缩包，供下载/上传使用

| Java | 文件 |
| --- | --- |
| 8 | OpenJDK8U-jre_aarch64_linux_hotspot_8u512b09.tar.xz |
| 11 | OpenJDK11U-jre_aarch64_linux_hotspot_11.0.33_10.tar.xz |
| 17 | OpenJDK17U-jre_aarch64_linux_hotspot_17.0.21_9.tar.xz |
| 21 | OpenJDK21U-jre_aarch64_linux_hotspot_21.0.13_9.tar.xz |
| 25 | OpenJDK25U-jre_aarch64_linux_hotspot_25.0.5_8.tar.xz |

> 说明：为便于上传分发，JRE 采用 xz 压缩（比 gzip 小约 35%）。App 的 `Dl.extractTar()` 已支持按魔数嗅探 tar.gz / tar.xz 两种格式，可直接解包使用。
