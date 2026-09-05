# 跑都不跑

当前版本：**v2.2.0**（versionCode 20200）

绘制路线、沿路线模拟定位、检查更新。

源码与发行页：https://github.com/caixukun-1-23/-apk-

## 检查更新怎么工作

App 会请求：

https://api.github.com/repos/caixukun-1-23/-apk-/releases/latest

把 GitHub Release 的 `tag_name` 和安装包里的 `versionName` 比较：

- tag 更高 → 弹出更新，点下载打开该 Release 里的 `.apk`
- 没有 apk 附件 → 打开 https://github.com/caixukun-1-23/-apk-/releases
- tag 相同或更低 → 提示已是最新

所以 **GitHub Release 的 tag 必须和 `app/build.gradle` 里的 versionName 对齐**。

| 操作 | 要改的 |
|---|---|
| 发当前这一版 | 打 tag `v2.2.0`，把 APK 传到这个 Release |
| 以后发新版 | 先把 versionName 改成 `v2.3.0`、versionCode 改成 `20300`，再打新 tag 上传 APK |

## 打开工程

Android Studio 打开本目录，不要只打开 `app`。

`local.properties` 不要提交。里面填 SDK 路径和百度地图 AK。

包名：`com.acooldog.toolbox`

## 运行前

1. 开发者选项 → 模拟位置应用 → 跑都不跑
2. 授予定位权限
3. 先绘制并保存路线，再去路线模拟
