# CONTEXT

## 当前正在做什么
- 正在收口 Windows 服务器部署结构，目标是把仓库里的重复部署副本移除，改成“源码只维护两处，上传只认一个生成目录”。
- 唯一网关源码目录固定为 `bridge/mt5_gateway`，唯一 Windows 部署脚本源码目录固定为 `deploy/tencent/windows`。
- 唯一服务器上传目录固定为 `dist/windows_server_bundle`，由 `python scripts/build_windows_server_bundle.py` 生成，并直接对应服务器目录 `C:\mt5_bundle\windows_server_bundle`。
- 正在修复双击 `deploy_bundle.cmd` 时批处理被 Windows 误读的问题，确保服务器侧可以直接双击完成部署。
- 正在把“App 远程驱动服务器切换 MT5 账号”整理成正式设计文档，准备进入实现计划阶段。

## 上次停在哪个位置
- 已完成 `scripts/build_windows_server_bundle.py`，可以实际生成 `dist/windows_server_bundle`，产物结构已扩展为 `mt5_gateway/ + windows/ + deploy_bundle.cmd + deploy_bundle.ps1 + README.md`。
- 已把 `deploy/tencent/windows` 下的启动、自启注册脚本改成同时兼容“完整仓库根目录”和“部署包根目录”两种布局，不再需要单独维护另一套 bundle 脚本。
- 已更新 `README.md`、`deploy/tencent/README.md`、`ARCHITECTURE.md` 的部署口径，旧的 `deploy/tencent/windows_server_bundle` 与历史 zip 也已删除。
- 已定位双击部署失败的直接原因：`deploy_bundle.cmd` 在仓库和生成产物中使用了 `LF` 换行，Windows `cmd.exe` 双击执行时会误解析，触发 `'65001'`、`'IR'` 不是命令的报错；现已改为构建时强制输出 `CRLF`，并补了自动测试防回退。
- 已进一步定位 PowerShell 语法报错的直接原因：部署包里的 `.ps1` 使用 `UTF-8 无 BOM`，在服务器的 Windows PowerShell 5.x 下会按错误编码读取，导致中文字符串乱码、引号错乱、`}` 和字符串终止符报错；现已改为构建时统一输出 `UTF-8 with BOM + CRLF`，并补了自动测试防回退。
- 已定位部署第 4 步“找不到 `02_register_startup_task.ps1`”的直接原因：第 3 步 bootstrap 脚本会把当前目录切到 `mt5_gateway`，而主部署脚本后续仍用相对路径调用 `windows` 目录下的脚本；现已改为在主部署脚本里提前拼出绝对路径调用，并补了自动测试防回退。
- 已定位部署第 6 步“找不到 `caddy.exe`”的直接原因：脚本之前只认 `windows_server_bundle\windows\caddy.exe`，而服务器现场实际把 `caddy.exe` 放在 `C:\mt5_bundle`；现已改为自动按“`windows` 目录 -> 部署包根目录 -> 上级目录”三层顺序查找，并补了自动测试防回退。
- 已完成“App 远程驱动服务器切换 MT5 账号”设计文档，路径为 `docs/superpowers/specs/2026-04-06-remote-mt5-account-session-design.md`；设计已明确采用 HTTPS + 服务器公钥加密登录 + 服务端加密保存账号档案 + 单激活账号会话的方案。

## 近期关键决定和原因
- 不再把 `deploy/tencent/windows_server_bundle` 作为仓库里的长期维护目录；原因是它和真实源码长期双份并存，已经多次导致修改位置漂移、部署文件不一致、服务器现场排查困难。
- 改为“源码两处 + 产物一处”的结构；原因是这样既保留清晰的开发目录，又能满足服务器侧“一次复制一个文件夹”的部署要求。
- 部署脚本统一兼容 `RepoRoot` 和 `BundleRoot`；原因是这样同一套脚本既能在仓库内调试，也能在服务器上传后的 `C:\mt5_bundle\windows_server_bundle` 里直接运行。
- `.cmd` 文件由构建脚本统一规范成 `CRLF`；原因是批处理双击执行依赖 Windows 自身的解析方式，不能把换行格式交给编辑器或 Git 自动决定。
- `.ps1` 文件由构建脚本统一规范成 `UTF-8 with BOM + CRLF`；原因是服务器现场实际运行的是 Windows PowerShell 5.x，不能假设其默认按 UTF-8 无 BOM 解析中文脚本。
- `caddy.exe` 位置由部署脚本自动兼容历史目录；原因是服务器现场已有 `C:\mt5_bundle\caddy.exe` 这种旧布局，迁移成本高，部署脚本应直接消化这种差异。
- 账号远程切换设计保持“单用户、多账号、任意时刻一个激活账号”；原因是它最贴合现有网关与交易架构，能用最小正确改动闭合安全、状态和同步链路。
