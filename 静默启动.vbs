' ============================================================
' 校园微集市 (CampusMicroMart) - 静默启动
' 双击本文件即可启动全部服务，不弹出任何窗口，不抢焦点。
'
' 关键：本机默认终端是 Windows Terminal，直接运行 powershell/cmd
'       会被 Terminal 接管并弹出可见窗口。因此这里用
'       conhost.exe --headless 以无窗口方式承载 PowerShell。
'
' 启动过程约 2 分钟；完成后浏览器访问 http://localhost
' 停止服务：powershell -ExecutionPolicy Bypass -File 项目目录\stop-background.ps1
' ============================================================
Option Explicit
Dim shell
Set shell = CreateObject("WScript.Shell")
shell.Run "conhost.exe --headless powershell.exe -NoProfile -ExecutionPolicy Bypass -File ""C:\CampusMart\boot-campusmart.ps1""", 0, False
Set shell = Nothing
