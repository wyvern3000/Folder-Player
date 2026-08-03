# FolderPlayer 可下载字体资源

此目录托管 FolderPlayer App 内「设置 → 字体」里几个可选字体的原始文件，
App 运行时通过 `raw.githubusercontent.com` 直链下载到设备本地，不随 APK
打包分发。全部三款都是原始官方文件的**逐字节镶镜**（未做任何裁剪/转码/
重新编译），未修改，符合各自 SIL Open Font License 1.1 对「未修改字体
可自由再分发」的要求。

更新这里的文件 = App 里对应字体的「新版本」，不需要改 App 代码，只要文件名
不变、直链路径不变即可（App 侧按固定路径拉取，没有版本号协商机制）。

## 文件清单

| 文件 | 对应字体 | 来源 | 上游版本 | 体积 |
|---|---|---|---|---|
| `NotoSansSC-Variable.ttf` | 思源黑体 | [google/fonts](https://github.com/google/fonts/blob/main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf)，`ofl/notosanssc/NotoSansSC[wght].ttf` | 无版本 tag，跟随该仓库主分支当前内容（下载时为 2026-08-03 状态） | 17,772,300 字节 |
| `LXGWWenKaiLite-Regular.ttf` | 霞鹜文楷（非国标 Lite 版，Regular 字重） | [lxgw/LxgwWenKai-Lite](https://github.com/lxgw/LxgwWenKai-Lite/releases) Release 资产 | `v1.522` | 13,872,424 字节 |
| `SarasaUiSC-Regular.ttf` | 更纱黑体 UI（Regular 字重，从官方 hinted 版 7z 整包里提取） | [be5invis/Sarasa-Gothic](https://github.com/be5invis/Sarasa-Gothic/releases) Release 资产 `SarasaUiSC-TTF-{version}.7z` | `v1.0.40` | 24,049,996 字节（提取自 62,870,273 字节的 7z 包） |

`licenses/` 子目录放对应的 OFL 协议全文，每个字体一份，来源同上表。

## 关于霞鹜文楷的一条协议细节（供以后维护者参考）

`licenses/LxgwWenKai-OFL.txt` 里有一条 ADDITIONAL PERMISSION，只限制
「Modified Versions（裁剪/转格式过的版本）」使用「霞鹜/LXGW」这个 Reserved
Font Name 时的场景（不能做成可安装的桌面字体在主流字体平台分发）。**这里
放的是原始未修改文件**，不受这条限制——但如果以后有人想在这个基础上自己
再裁一次子集（比如为了进一步缩小体积），要重新读一下这条条款，别直接照抄
现在这个「直接镶镜官方文件」的逻辑。

## 关于更纱黑体的一个取舍（供以后维护者参考）

官方 Release 不发裸单文件，只发按脚本整字重打包的压缩包，且只有 7z 格式。
这里下载的是 `SarasaUiSC-TTF-1.0.40.7z`（hinted 版，62.87MB），用 7z 解压后
只留 `SarasaUiSC-Regular.ttf` 一个文件（24.05MB），其余字重全部丢弃。选
hinted 而不是 Unhinted 版本，是因为官方文档原话建议一般用户选 hinted，
Unhinted 只在「需要更小文件体积且不在意显示效果」的极端场景才选。如果以后
升级到更新的上游版本，同样的流程：下整包 7z → 解压只取 Regular → 丢弃
压缩包本身，不要把 7z 包本身提交进这个仓库。

## App 侧下载直链（供参考，实际以 App 代码里配置的为准）

```
https://raw.githubusercontent.com/wyvern3000/Folder-Player/main/fonts/NotoSansSC-Variable.ttf
https://raw.githubusercontent.com/wyvern3000/Folder-Player/main/fonts/LXGWWenKaiLite-Regular.ttf
https://raw.githubusercontent.com/wyvern3000/Folder-Player/main/fonts/SarasaUiSC-Regular.ttf
```
