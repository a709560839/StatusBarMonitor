# MIUI状态栏监视器 StatusBarMonitor

[![License](https://img.shields.io/badge/License%20-GPLv3.0%20-337ab7.svg)](https://www.gnu.org/licenses/gpl-3.0.html#license-text)
[![Download](https://img.shields.io/badge/下载%20-Release%20-5ce500.svg)](https://github.com/a709560839/StatusBarMonitor/releases)

# 如何使用：
1. 直接安装。
2. Lsposed启用作用域：系统界面。
3. 给root权限**(注意：不需要检测GPU的话不用给root权限)**
4. 打开App：重启作用域。

# 使用效果：
<div>
<img src="https://github.com/a709560839/StatusBarMonitor/blob/main/screenshot/1.jpg" width="555">
</div>
<div>
<img src="https://github.com/a709560839/StatusBarMonitor/blob/main/screenshot/2.jpg" width="555">
</div>

# App界面：
<div>
<img src="https://github.com/a709560839/StatusBarMonitor/blob/main/screenshot/3.png" width="333">
</div>

# 自测可行版本：
官方ROM MIUI 13.0.5  Android 12

# 碎碎念：
目前AI使用常驻su进程cat获取GPU信息，性能弱于scene的守护进程。然后展示的信息和位置没有做成可选项。兄弟们要是fork改好了记得在酷安里@我，我伸手嘿嘿
# [酷安原帖](https://www.coolapk.com/feed/71718136)

# 最后：
搞机有风险，Magisk救砖模块装了吗(ksu等内核管理器倒是自带救砖)，没装也没事，进twrp文件管理，/data/adb/modules 删对应的模块就行。  
最后的最后，各位觉得有用的，点点Star。
