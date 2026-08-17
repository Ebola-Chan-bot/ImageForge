#Requires -Version 5.1
<#
.SYNOPSIS
    ImageForge 一键部署脚本：自动补齐构建依赖、编译 debug 包并部署到 USB 连接的 Android 手机。

.DESCRIPTION
    依赖解析策略（针对 JDK、Android SDK 等每个依赖）：
      1. 优先使用本脚本目录下"部署配置.ini"中记录过的路径；
      2. 未命中则搜索常见安装路径；
      3. 仍未命中则交互询问用户是否已有安装好的依赖：
         - 用户输入非空路径：不做额外检查直接使用，并记录到配置文件，以后不再询问；
         - 用户直接回车：视为未安装，由脚本自动下载安装。

    约束：
      - 不会把任何已安装的包退回旧版，也不会另外安装旧版；遇到版本不兼容时停止并请人工介入；
      - 不在 ImageForge 仓库内产生任何未被 .gitignore 覆盖的改动
        （构建产物 build/、.gradle/、local.properties 等均已被 .gitignore 覆盖；
         JDK / Android SDK 安装在用户目录 %LOCALAPPDATA% 下，仓库外）；
      - 所有自动安装默认放在 %LOCALAPPDATA%\ImageForgeDeployTools 与 %LOCALAPPDATA%\Android\Sdk，无需管理员权限。

.NOTES
    用法：将 Android 手机用 USB 连接电脑后，直接运行本脚本即可。
#>

$ErrorActionPreference = 'Stop'
if (-not ([Net.ServicePointManager]::SecurityProtocol -band [Net.SecurityProtocolType]::Tls12)) {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
}

# ============================ 基础路径与常量 ============================
$脚本目录     = $PSScriptRoot
$项目目录     = Join-Path (Split-Path $脚本目录 -Parent) 'ImageForge'
$配置文件路径   = Join-Path $脚本目录 '部署配置.ini'
$工具安装根目录 = Join-Path $env:LOCALAPPDATA 'ImageForgeDeployTools'

# 以下信息不在此处写死，运行时从工作区项目文件动态解析（见"工作区项目信息解析"）：
#   应用包名（applicationId + debug 构建类型的后缀）
#   主 Activity（AndroidManifest 中带 MAIN/LAUNCHER intent-filter 的 activity）
#   compileSdk、最低 JDK 版本（compileOptions）、Gradle 支持运行的最高 Java 版本
$应用包名 = $null
$主Activity = $null
$最低JDK主版本 = $null
$最高JDK主版本 = $null
$编译Sdk版本 = $null

function 停止并询问 {
    param([string]$原因)
    Write-Host ''
    Write-Host '==========================================================' -ForegroundColor Red
    Write-Host '  需要人工介入，脚本已停止：' -ForegroundColor Red
    Write-Host "  $原因" -ForegroundColor Red
    Write-Host '==========================================================' -ForegroundColor Red
    throw $原因
}

function 下载文件 {
    param(
        [string[]]$链接列表,
        [string]$目标路径
    )
    if (Test-Path $目标路径) { Remove-Item $目标路径 -Force }
    foreach ($链接 in $链接列表) {
        try {
            Write-Host "  ⬇ 正在下载：$链接" -ForegroundColor DarkGray
            Invoke-WebRequest -Uri $链接 -OutFile $目标路径 -UseBasicParsing -TimeoutSec 1800
            if ((Test-Path $目标路径) -and (Get-Item $目标路径).Length -gt 0) { return }
        }
        catch {
            Write-Host "  ✗ 该链接下载失败：$($_.Exception.Message)" -ForegroundColor DarkYellow
        }
    }
    停止并询问 "所有下载源均失败，无法下载文件。请检查网络（或代理）后重试；若你已有本地安装包，可将对应依赖路径手工提供给本脚本。"
}

function 运行外部命令 {
    # PowerShell 5.1 下，$ErrorActionPreference='Stop'（脚本全局设置）会把
    # 外部命令写入 stderr 的每一行升级成终止性异常（例如 java -version 就是写 stderr）。
    # 这里统一在 Continue 策略下运行外部命令并合并 stderr，只靠退出码判断成败。
    param(
        [Parameter(Mandatory = $true)][string]$可执行文件,
        [AllowEmptyCollection()][string[]]$命令参数 = @()
    )
    $原有策略 = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $可执行文件 @命令参数 2>&1
    }
    finally {
        $ErrorActionPreference = $原有策略
    }
}

# ---------------------------- 配置文件读写 ----------------------------
function 读取配置 {
    $配置表 = @{}
    if (Test-Path $配置文件路径) {
        foreach ($行 in (Get-Content $配置文件路径 -Encoding UTF8)) {
            if ($行 -match '^\s*([^#=\s][^=]*?)\s*=\s*(.*)\s*$') {
                $配置表[$Matches[1]] = $Matches[2]
            }
        }
    }
    return $配置表
}

function 记录配置 {
    param(
        [string]$键名,
        [string]$值
    )
    $现有行 = @()
    if (Test-Path $配置文件路径) { $现有行 = @(Get-Content $配置文件路径 -Encoding UTF8) }
    $新行集合 = New-Object System.Collections.Generic.List[string]
    $已替换 = $false
    foreach ($行 in $现有行) {
        if ($行 -match "^\s*$([regex]::Escape($键名))\s*=") {
            $新行集合.Add("$键名=$值")
            $已替换 = $true
        }
        else { $新行集合.Add($行) }
    }
    if (-not $已替换) { $新行集合.Add("$键名=$值") }
    [IO.File]::WriteAllLines($配置文件路径, $新行集合, (New-Object Text.UTF8Encoding($true)))
    Write-Host "  ✔ 已记录到配置文件（$($配置文件路径 | Split-Path -Leaf)）：$键名=$值" -ForegroundColor DarkGray
}

# ---------------------------- 工作区项目信息解析 ----------------------------
# 所有与应用相关的关键信息均从工作区源文件中解析，脚本内不写死。
# XML 解析使用 .NET 的 System.Xml（XmlDocument + XPath），不做手写正则解析。
function 解析Gradle项目信息 {
    $信息 = @{}
    $应用构建文件 = Join-Path $项目目录 'app\build.gradle.kts'
    $manifest文件 = Join-Path $项目目录 'app\src\main\AndroidManifest.xml'
    if (-not (Test-Path $应用构建文件)) { 停止并询问 "找不到应用构建文件：$应用构建文件，无法解析项目信息。" }
    $构建内容 = Get-Content $应用构建文件 -Raw -Encoding UTF8

    # 应用包名：applicationId，若 debug 构建类型声明了 applicationIdSuffix 则拼接（deploy 的是 debug 变体）
    if ($构建内容 -match 'applicationId\s*=\s*"([^"]+)"') {
        $包名 = $Matches[1]
    }
    else {
        停止并询问 '无法从 app/build.gradle.kts 中解析出 applicationId，请检查项目文件。'
    }
    if ($构建内容 -match '(?s)debug\s*\{[^}]*?applicationIdSuffix\s*=\s*"([^"]*)"[^}]*\}') {
        $包名 = $包名 + $Matches[1]
    }
    $信息['应用包名'] = $包名

    # compileSdk（用于预装对应 platform；解析失败时由 Gradle 构建时自动补齐）
    if ($构建内容 -match 'compileSdk\s*=\s*(\d+)') { $信息['编译Sdk'] = [int]$Matches[1] } else { $信息['编译Sdk'] = $null }

    # 项目要求的最低 Java 版本：compileOptions 中的 sourceCompatibility
    if ($构建内容 -match 'JavaVersion\.VERSION_(\d+)') { $信息['最低JDK'] = [int]$Matches[1] } else { $信息['最低JDK'] = 17 }

    # 命名空间（manifest 中以 "." 开头的相对类名需用它补全）
    if ($构建内容 -match 'namespace\s*=\s*"([^"]+)"') { $信息['命名空间'] = $Matches[1] } else { $信息['命名空间'] = '' }

    # 主 Activity：AndroidManifest.xml 中声明了 MAIN + LAUNCHER intent-filter 的 activity（用 .NET XML API 解析）
    if (-not (Test-Path $manifest文件)) { 停止并询问 "找不到清单文件：$manifest文件" }
    $manifest文档 = New-Object Xml.XmlDocument
    try {
        # XmlReader 能自动识别文件开头的 UTF-8 BOM / 编码声明，比先读字符串更稳健
        $manifest文档.Load($manifest文件)
    }
    catch {
        停止并询问 "解析清单文件失败：$($_.Exception.Message)"
    }
    $名称管理器 = New-Object Xml.XmlNamespaceManager($manifest文档.NameTable)
    $名称管理器.AddNamespace('android', 'http://schemas.android.com/apk/res/android')
    $启动节点 = $manifest文档.SelectSingleNode(
        "//activity[intent-filter/action[@android:name='android.intent.action.MAIN'] and intent-filter/category[@android:name='android.intent.category.LAUNCHER']]",
        $名称管理器)
    if (-not $启动节点) { 停止并询问 '无法从 AndroidManifest.xml 中定位带 MAIN/LAUNCHER intent-filter 的主 Activity，请检查项目。' }
    $主Activity名称 = $启动节点.GetAttribute('name', 'http://schemas.android.com/apk/res/android')
    if (-not $主Activity名称) { 停止并询问 '主 Activity 节点缺少 android:name 属性，请检查清单文件。' }
    if ($主Activity名称.StartsWith('.')) { $主Activity名称 = $信息['命名空间'] + $主Activity名称 }
    $信息['主Activity'] = $主Activity名称
    return $信息
}

function 解析Gradle支持的最高Java版本 {
    # Gradle 本体可运行的最高 Java 版本由 Gradle 发行版决定，版本号从工作区的 wrapper 配置解析；
    # "版本 → 支持上限"对应关系来自 Gradle 官方兼容性说明（工具链知识）。
    $wrapper配置 = Join-Path $项目目录 'gradle\wrapper\gradle-wrapper.properties'
    if (-not (Test-Path $wrapper配置)) { return $null }
    $内容 = Get-Content $wrapper配置 -Raw
    if ($内容 -notmatch 'gradle-(\d+)\.(\d+)') { return $null }
    $主 = [int]$Matches[1]
    $次 = [int]$Matches[2]
    if ($主 -gt 8 -or ($主 -eq 8 -and $次 -ge 13)) { return 24 }
    if ($主 -eq 8 -and $次 -ge 10) { return 23 }
    if ($主 -eq 8 -and $次 -ge 8) { return 22 }
    if ($主 -eq 8 -and $次 -ge 5) { return 21 }
    if ($主 -eq 8) { return 19 }
    if ($主 -ge 7) { return 18 }
    return $null
}

# ---------------------------- 依赖定位通用逻辑 ----------------------------
function 定位依赖 {
    param(
        [string]$依赖名称,
        [string]$配置键名,
        [hashtable]$配置表,
        [string[]]$常见路径列表,
        [scriptblock]$验证器,      # 入参：候选路径；返回验证通过后的最终路径，失败返回 $null
        [scriptblock]$自动安装器   # 无返回值要求，返回安装后的路径
    )
    # 1. 已记录路径优先
    if ($配置表.ContainsKey($配置键名) -and $配置表[$配置键名]) {
        Write-Host "  ➤ 尝试配置文件中记录的 $依赖名称 路径：$($配置表[$配置键名])" -ForegroundColor DarkGray
        $已验证 = & $验证器 $配置表[$配置键名]
        if ($已验证) { return $已验证 }
        Write-Host "  ✗ 记录的路径已失效，继续搜索常见路径..." -ForegroundColor DarkYellow
    }
    # 2. 常见路径搜索
    foreach ($候选 in $常见路径列表) {
        if (-not $候选) { continue }
        $已验证 = & $验证器 $候选
        if ($已验证) {
            Write-Host "  ✔ 在常见路径找到 $依赖名称：$已验证" -ForegroundColor Green
            return $已验证
        }
    }
    # 3. 交互询问
    Write-Host ''
    $用户输入 = Read-Host "❓ 未找到 $依赖名称。如果你已经安装过，请输入它的安装路径；直接回车则由脚本自动安装"
    if ($用户输入 -and $用户输入.Trim()) {
        $路径 = $用户输入.Trim().Trim('"').Trim("'")
        Write-Host "  ✔ 使用你提供的 $依赖名称 路径：$路径" -ForegroundColor Green
        记录配置 -键名 $配置键名 -值 $路径
        return $路径
    }
    # 4. 自动安装
    Write-Host "  ➤ 开始自动安装 $依赖名称 ..." -ForegroundColor Cyan
    $安装路径 = & $自动安装器
    if (-not $安装路径) { 停止并询问 "$依赖名称 自动安装失败。" }
    记录配置 -键名 $配置键名 -值 $安装路径
    Write-Host "  ✔ $依赖名称 自动安装完成：$安装路径" -ForegroundColor Green
    return $安装路径
}

# ============================ JDK ============================
function 获取Java主版本 {
    param([string]$java可执行文件路径)
    try {
        $原始输出 = (运行外部命令 -可执行文件 $java可执行文件路径 -命令参数 '-version' | Out-String)
        if ($原始输出 -match 'version "(\d+)(?:\.(\d+))?') {
            $主版本 = [int]$Matches[1]
            if ($主版本 -eq 1 -and $Matches[2]) { $主版本 = [int]$Matches[2] }   # 1.8 之类的旧格式
            return $主版本
        }
    }
    catch { }
    return $null
}

$JDK验证器 = {
    param([string]$候选路径)
    if (-not $候选路径) { return $null }
    $java可执行文件 = Join-Path $候选路径 'bin\java.exe'
    if (-not (Test-Path $java可执行文件)) { return $null }
    $主版本 = 获取Java主版本 $java可执行文件
    if ($null -eq $主版本) { return $null }
    if ($null -ne $最低JDK主版本 -and $主版本 -lt $最低JDK主版本) {
        Write-Host "  ✗ 发现 JDK $主版本，低于项目要求的 $最低JDK主版本，跳过（将另行安装新版，不影响已有安装）" -ForegroundColor DarkYellow
        return $null
    }
    if ($null -ne $最高JDK主版本 -and $主版本 -gt $最高JDK主版本) {
        停止并询问 "检测到已安装 JDK 版本 $主版本 高于项目构建工具链（Gradle）支持的上限 $最高JDK主版本。`n按约束不能降级或另装旧版本，请人工确认如何处理。"
    }
    return $候选路径
}

function 测试目录可写 {
    param([string]$目录路径)
    try {
        [IO.Directory]::Exists($目录路径) | Out-Null
        $探测文件 = Join-Path $目录路径 ('.写权限探测-' + [Guid]::NewGuid().ToString('N'))
        [IO.File]::WriteAllText($探测文件, 'x')
        Remove-Item $探测文件 -Force -ErrorAction SilentlyContinue
        return $true
    }
    catch { return $false }
}

function 获取Adoptium下载链接 {
    param([int]$主版本)
    try {
        $资产 = Invoke-RestMethod "https://api.adoptium.net/v3/assets/latest/$主版本/hotspot?os=windows&architecture=x64&image_type=jdk" -UseBasicParsing -TimeoutSec 60
        $资产 | Select-Object -First 1 | ForEach-Object { $_.binary.package.link }
    }
    catch { $null }
}

function 自动安装JDK {
    $安装根目录 = Join-Path $工具安装根目录 'JDK21'
    $压缩包路径 = Join-Path $env:TEMP 'imageforge-jdk21.zip'
    $动态链接 = 获取Adoptium下载链接 21
    $链接候选 = @()
    if ($动态链接) { $链接候选 += $动态链接 }
    # 备用：直接重定向接口（无 feature 路径后缀）
    $链接候选 += 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal'
    下载文件 -链接列表 $链接候选 -目标路径 $压缩包路径
    Write-Host "  ⏳ 正在解压 JDK（可能需要一两分钟）..." -ForegroundColor DarkGray
    if (Test-Path $安装根目录) { Remove-Item $安装根目录 -Recurse -Force }
    New-Item -ItemType Directory -Path $安装根目录 -Force | Out-Null
    Expand-Archive -Path $压缩包路径 -DestinationPath $安装根目录 -Force
    $JDK根 = Get-ChildItem $安装根目录 -Directory | Select-Object -First 1
    $java可执行文件 = Join-Path $JDK根.FullName 'bin\java.exe'
    if (-not (Test-Path $java可执行文件)) { 停止并询问 'JDK 解压后的目录结构与预期不符，请检查。' }
    return $JDK根.FullName
}

# ============================ Android SDK ============================
$Sdk验证器 = {
    param([string]$候选路径)
    if (-not $候选路径) { return $null }
    $sdkmanager = Join-Path $候选路径 'cmdline-tools\latest\bin\sdkmanager.bat'
    $adb路径    = Join-Path $候选路径 'platform-tools\adb.exe'
    if ((Test-Path $sdkmanager) -or (Test-Path $adb路径)) {
        # 只读 SDK（如 Program Files 下）也直接使用：通过构建时注入 buildToolsVersion，
        # 通常无需再向 SDK 写入任何东西。仅当后续确实需要补装组件时才检查写权限。
        if (-not (测试目录可写 $候选路径)) {
            Write-Host "  ℹ 该 SDK（$候选路径）对当前账户只读：将直接使用，构建时通过注入避开额外组件安装；若仍缺组件会停下来问你。" -ForegroundColor DarkGray
        }
        return $候选路径
    }
    return $null
}

function 安装Sdk组件 {
    param(
        [string]$Sdk根目录,
        [string[]]$包列表
    )
    $sdkmanager = Join-Path $Sdk根目录 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (-not (Test-Path $sdkmanager)) { return $false }
    if ($包列表.Count -gt 0) {
        Write-Host "  ⏳ 正在安装 Android SDK 组件：$($包列表 -join ', ')（首次安装需下载，请耐心等待）..." -ForegroundColor DarkGray
    }
    # 用连续的 y 接受所有许可协议
    $y输入 = ("y`n" * 40)
    $旧策略 = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $y输入 | & cmd /c "`"$sdkmanager`" --sdk_root=`"$Sdk根目录`" --licenses 2>&1" | Out-Null
        if ($global:LASTEXITCODE -ne 0) { Write-Host '  ✗ 接受 SDK 许可协议出现问题（可能已接受过，继续）' -ForegroundColor DarkYellow }
        $包参数 = ($包列表 | ForEach-Object { "`"$_`"" }) -join ' '
        $y输入 | & cmd /c "`"$sdkmanager`" --sdk_root=`"$Sdk根目录`" $包参数 2>&1" | Out-Null
        if ($global:LASTEXITCODE -ne 0) { return $false }
    }
    finally {
        $ErrorActionPreference = $旧策略
    }
    return $true
}

function 自动安装AndroidSdk {
    $Sdk根目录 = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    $压缩包路径 = Join-Path $env:TEMP 'imageforge-cmdline-tools.zip'
    New-Item -ItemType Directory -Path (Join-Path $Sdk根目录 'cmdline-tools') -Force | Out-Null
    下载文件 -链接列表 @(
        'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip',
        'https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-win-11076708_latest.zip'
    ) -目标路径 $压缩包路径
    Write-Host '  ⏳ 正在解压 Android SDK 命令行工具...' -ForegroundColor DarkGray
    Expand-Archive -Path $压缩包路径 -DestinationPath (Join-Path $Sdk根目录 'cmdline-tools') -Force
    $解压临时目录 = Join-Path $Sdk根目录 'cmdline-tools\cmdline-tools'
    $latest目录  = Join-Path $Sdk根目录 'cmdline-tools\latest'
    if (Test-Path $latest目录) { Remove-Item $latest目录 -Recurse -Force }
    Move-Item $解压临时目录 $latest目录
    # 安装平台工具（adb）与项目 compileSdk 对应的平台（compileSdk 解析失败时只装 adb，其余由 Gradle 自动补齐）
    $包列表 = @('platform-tools')
    if ($null -ne $编译Sdk版本) { $包列表 += "platforms;android-$编译Sdk版本" }
    $成功 = 安装Sdk组件 -Sdk根目录 $Sdk根目录 -包列表 $包列表
    if (-not $成功) {
        Write-Host '  ✗ SDK 组件安装出现问题，稍后由 Gradle 构建时自动补齐（许可协议已接受）。' -ForegroundColor DarkYellow
    }
    return $Sdk根目录
}

function 生成构建注入脚本 {
    # 目的：避免额外安装更旧的 build-tools 版本。AGP 默认的 buildToolsVersion 与项目实际安装的
    # build-tools 可能不一致（例如 AGP 默认 35.0.0，而本机 SDK 只有 36.0.0）。
    # 通过 Gradle init script（-I 参数）在 AGP 应用之后把 buildToolsVersion 覆盖为与 compileSdk 一致的版本，
    # 不修改任何 git 跟踪的工程文件。此脚本每次运行时动态生成，内容中的版本号来自 compileSdk 的解析结果。
    param([int]$compileSdk版本)
    $注入脚本路径 = Join-Path $env:TEMP 'imageforge-build-tools-override.gradle'
    $注入内容 = @"
// 自动生成的 Gradle init script：将 AGP 的 buildToolsVersion 对齐到 compileSdk 对应的版本，
// 避免安装 AGP 默认的更旧 build-tools。AGP 对该属性的设置已废弃（未来版本可能移除），届时需去掉本注入。
// 注意：必须在插件应用的时刻（plugins.withId）设置；projectsEvaluated 时 AGP 已冻结配置，会被拒绝。
allprojects { proj ->
    proj.plugins.withId('com.android.application') {
        def androidExtension = proj.extensions.findByName('android')
        if (androidExtension != null) {
            androidExtension.buildToolsVersion = '${compileSdk版本}.0.0'
        }
    }
}
"@
    # 必须用无 BOM 的 UTF-8：文件头的 BOM 会让 Groovy 报 "Unexpected character"
    [IO.File]::WriteAllText($注入脚本路径, $注入内容, (New-Object Text.UTF8Encoding($false)))
    return $注入脚本路径
}

# ---------------------------- Gradle 发行版缓存预热 ----------------------------
# 目的：让 gradlew 不需要联网下载发行版，同时不修改任何 git 跟踪文件。
# 缓存布局与跳过下载的判定规则来自 Gradle 官方 wrapper 源码（PathAssembler / Install）：
#   目录：<GRADLE_USER_HOME>/wrapper/dists/<zip文件名去后缀>/<URL的MD5哈希(36进制)>/
#   判定：解压目录存在 + <zip文件名>.ok 标记存在 => 直接使用，不下载
function 转36进制 {
    param([System.Numerics.BigInteger]$大整数)
    $字符集 = '0123456789abcdefghijklmnopqrstuvwxyz'
    if ($大整数 -eq 0) { return '0' }
    $字符列表 = New-Object System.Collections.Generic.List[char]
    $数值 = $大整数
    $三十六 = [System.Numerics.BigInteger]::new(36)
    while ($数值 -gt 0) {
        $余数 = [System.Numerics.BigInteger]::Zero
        $数值 = [System.Numerics.BigInteger]::DivRem($数值, $三十六, [ref]$余数)
        $字符列表.Insert(0, $字符集[[int]$余数])
    }
    return (-join $字符列表)
}

function 计算Wrapper缓存哈希 {
    param([string]$字符串)
    # 与 org.gradle.wrapper.PathAssembler.getHash 一致：
    #   1) MD5(UTF-8 字节)  2) 作为无符号大整数  3) 转 36 进制(0-9a-z)
    [void][Reflection.Assembly]::LoadWithPartialName('System.Numerics')
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $摘要 = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($字符串))
    # Java： new BigInteger(1, digest) 是无符号大端；.NET BigInteger(byte[]) 是带符号小端，先补零高字节再反转
    $无符号字节 = New-Object byte[] ($摘要.Length + 1)
    [Array]::Copy($摘要, 0, $无符号字节, 1, $摘要.Length)
    [Array]::Reverse($无符号字节)
    return (转36进制 ([System.Numerics.BigInteger]::new($无符号字节)))
}

function 解析发行版链接 {
    param([string]$properties内容)
    foreach ($行 in ($properties内容 -split "`r?`n")) {
        if ($行 -match '^\s*distributionUrl\s*=\s*(.+)$') {
            return ($Matches[1].Trim() -replace '\\:', ':')
        }
    }
    return $null
}

function 转换发行版镜像链接列表 {
    param([string]$原始链接)
    # 发行版 zip 文件名在各镜像站与官方一致，替换下载站点即可
    if ($原始链接 -match '/(gradle-[^/]+\.zip)$') {
        $文件名 = $Matches[1]
        return @(
            ('https://mirrors.cloud.tencent.com/gradle/' + $文件名),
            $原始链接
        )
    }
    return @($原始链接)
}

function 获取HEAD版发行版链接 {
    # 基准：git 仓库中跟踪文件的原始内容。以它为缓存预热目标，
    # 这样无论工作区文件是否被临时改成镜像链接，gradlew 都能零下载构建，
    # 且最终可以把工作区的临时改动恢复掉（不产生未跟踪/未忽略的文件改动）。
    # 文件未入库或未安装 git 时，退回使用当前文件内容。
    # git -C 直接指定仓库路径，全程不改变当前工作目录。
    $wrapper配置 = Join-Path $项目目录 'gradle\wrapper\gradle-wrapper.properties'
    if (-not (Test-Path $wrapper配置)) { return $null }
    $内容 = $null
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $内容 = (运行外部命令 -可执行文件 'git' -命令参数 @('-C', $项目目录, 'show', 'HEAD:gradle/wrapper/gradle-wrapper.properties') | Out-String).Trim()
    }
    if (-not $内容) { $内容 = Get-Content $wrapper配置 -Raw }
    return (解析发行版链接 $内容)
}

function 转换发行版镜像链接列表 {
    param([string]$原始链接)
    # 发行版 zip 文件名在各镜像站与官方一致，替换下载站点即可
    if ($原始链接 -match '/(gradle-[^/]+\.zip)$') {
        $文件名 = $Matches[1]
        return @(
            ('https://mirrors.cloud.tencent.com/gradle/' + $文件名),
            $原始链接
        )
    }
    return @($原始链接)
}

function 预热Gradle发行版缓存 {
    param([string]$发行版链接)
    $文件名 = $发行版链接.Substring($发行版链接.LastIndexOf('/') + 1)
    $发行版名 = $文件名 -replace '\.zip$', ''
    $gradleHome名 = $发行版名 -replace '-(bin|all)$', ''
    $缓存哈希 = 计算Wrapper缓存哈希 $发行版链接
    $gradleUserHome = $env:GRADLE_USER_HOME
    if (-not $gradleUserHome) { $gradleUserHome = Join-Path $env:USERPROFILE '.gradle' }
    $校验目录 = Join-Path $gradleUserHome "wrapper\dists\$发行版名\$缓存哈希"
    $标记文件 = Join-Path $校验目录 ($文件名 + '.ok')
    $发行版主目录 = Join-Path $校验目录 $gradleHome名
    if ((Test-Path $标记文件) -and (Test-Path (Join-Path $发行版主目录 'bin\gradle.bat'))) {
        Write-Host ('  ✔ Gradle 发行版缓存已就位（' + $gradleHome名 + '），gradlew 无需联网下载') -ForegroundColor DarkGray
        return
    }
    New-Item -ItemType Directory -Path $校验目录 -Force | Out-Null
    # 优先复用本机其他哈希目录下已解压的同版本发行版（此前经镜像下载，与官方版为同一 zip）
    $dists根 = Join-Path $gradleUserHome 'wrapper\dists'
    $已有同版本 = @()
    if (Test-Path $dists根) {
        $已有同版本 = @(Get-ChildItem $dists根 -Directory -ErrorAction SilentlyContinue |
            Get-ChildItem -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName $gradleHome名 } |
            Where-Object { Test-Path (Join-Path $_ 'bin\gradle.bat') })
    }
    if ($已有同版本.Count -gt 0) {
        Write-Host ('  ➤ 复用本机已解压的 Gradle 发行版：' + $已有同版本[0]) -ForegroundColor Cyan
        Copy-Item -Path $已有同版本[0] -Destination $发行版主目录 -Recurse -Force
    }
    else {
        $压缩包 = Join-Path $env:TEMP $文件名
        下载文件 -链接列表 (转换发行版镜像链接列表 $发行版链接) -目标路径 $压缩包
        Write-Host '  ⏳ 正在解压 Gradle 发行版...' -ForegroundColor DarkGray
        Expand-Archive -Path $压缩包 -DestinationPath $校验目录 -Force
    }
    if (-not (Test-Path (Join-Path $发行版主目录 'bin\gradle.bat'))) {
        停止并询问 ('Gradle 发行版缓存准备失败：目录 ' + $校验目录 + ' 内未找到预期的 Gradle 安装结构。')
    }
    # wrapper 以 .ok 标记识别"可安全使用"的发行版；没有该标记 wrapper 会重新走下载流程
    [IO.File]::WriteAllText($标记文件, '')
    Write-Host ('  ✔ 已预热 Gradle 发行版缓存：' + $校验目录) -ForegroundColor Green
}

function 还原被修改的Wrapper配置 {
    # 纪律：不在仓库内保留对 git 跟踪文件的改动。
    # 若 gradle-wrapper.properties 相对 HEAD 有改动（比如之前为了走镜像直接改了下载源），
    # 恢复为仓库原始内容；镜像加速改由"预热发行版缓存"在仓库外实现。
    # git -C 直接指定仓库路径，全程不改变当前工作目录。
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { return }
    运行外部命令 -可执行文件 'git' -命令参数 @('-C', $项目目录, 'diff', '--quiet', '--', 'gradle/wrapper/gradle-wrapper.properties') | Out-Null
    $有差异 = ($global:LASTEXITCODE -ne 0)
    if ($有差异) {
        运行外部命令 -可执行文件 'git' -命令参数 @('-C', $项目目录, 'checkout', 'HEAD', '--', 'gradle/wrapper/gradle-wrapper.properties') | Out-Null
        if ($global:LASTEXITCODE -eq 0) {
            Write-Host '  ✔ 已将 gradle-wrapper.properties 恢复为仓库原始内容（镜像加速改由缓存预热实现）' -ForegroundColor Green
        }
        else {
            Write-Host '  ✗ 恢复 gradle-wrapper.properties 失败，请人工检查该文件的改动' -ForegroundColor Red
        }
    }
}

function 从构建日志提取缺失SDK包 {
    # 从形如 "build-tools;35.0.0 Android SDK Build-Tools 35" 的行中提取包 ID
    param([string]$日志内容)
    $包ID集合 = New-Object System.Collections.Generic.List[string]
    foreach ($匹配 in [regex]::Matches($日志内容, '(?m)^\s{2,}([a-zA-Z][a-zA-Z0-9_\-]*(?:;[\w.\-]+)+)\s')) {
        if (-not $包ID集合.Contains($匹配.Groups[1].Value)) { $包ID集合.Add($匹配.Groups[1].Value) }
    }
    return @($包ID集合)
}

# ============================ 设备与部署 ============================
function 定位adb {
    param([string]$Sdk根目录)
    $adb路径 = Join-Path $Sdk根目录 'platform-tools\adb.exe'
    if (Test-Path $adb路径) { return $adb路径 }
    # 尝试通过 sdkmanager 补装 platform-tools
    if (安装Sdk组件 -Sdk根目录 $Sdk根目录 -包列表 @('platform-tools') -and (Test-Path $adb路径)) {
        return $adb路径
    }
    停止并询问 "Android SDK 中没有 adb（platform-tools），且自动安装失败。请检查 SDK 目录：$Sdk根目录"
}

function 检测手机USB痕迹 {
    # 只读查询：Windows 当前已识别的手机相关 USB 设备（ADB/HDB 接口、MTP 便携设备等）。
    # 用于区分"手机根本没插好"与"系统看得到手机但 adb 枚举不到"两种情况。
    $所有设备 = Get-PnpDevice -ErrorAction SilentlyContinue
    if (-not $所有设备) { return @() }
    $匹配设备 = @($所有设备 | Where-Object {
            $_.Status -eq 'OK' -and ($_.FriendlyName -match 'Android|ADB|HDB|MTP|Honor|HONOR|Huawei|华为|荣耀')
        } | Select-Object -ExpandProperty FriendlyName -Unique)
    return $匹配设备
}

function 等待Android设备 {
    param(
        [string]$adb路径,
        [int]$超时秒 = 240
    )
    # 重启 adb 服务，尽量识别新插入的设备
    运行外部命令 -可执行文件 $adb路径 -命令参数 'kill-server' | Out-Null
    运行外部命令 -可执行文件 $adb路径 -命令参数 'start-server' | Out-Null
    Start-Sleep -Seconds 1
    $计时器 = [Diagnostics.Stopwatch]::StartNew()
    $首次提示已输出 = $false
    $授权提示已输出 = $false
    $HDB劫持提示已输出 = $false
    while ($计时器.Elapsed.TotalSeconds -lt $超时秒) {
        $输出 = (运行外部命令 -可执行文件 $adb路径 -命令参数 'devices' | Out-String)
        $已识别设备数 = 0
        foreach ($行 in ($输出 -split "`r?`n")) {
            if ($行 -match '^([A-Za-z0-9\.:_-]+)\s+(device|unauthorized|offline)\s*$') {
                $序列号 = $Matches[1]
                $状态   = $Matches[2]
                $已识别设备数++
                if ($状态 -eq 'device') { return $序列号 }
                if ($状态 -eq 'unauthorized' -and -not $授权提示已输出) {
                    Write-Host '  ⏳ 设备已连接但未授权：请在手机屏幕上点击"允许 USB 调试"（建议勾选"始终允许"）。脚本将继续等待...' -ForegroundColor Yellow
                    $授权提示已输出 = $true
                }
                elseif ($状态 -eq 'offline' -and -not $授权提示已输出) {
                    Write-Host '  ⏳ 设备状态异常（offline），请重新插拔 USB 或重新开关一次 USB 调试。脚本将继续等待...' -ForegroundColor Yellow
                }
            }
        }
        # 注意：不能直接用 "$输出 -notmatch 'device'" 判断无设备——
        # adb devices 的表头 "List of devices attached" 本身就含 devices 字样。
        if (-not $首次提示已输出 -and ($已识别设备数 -eq 0)) {
            Write-Host '  ⏳ 尚未在 adb 中检测到 Android 设备。请确认手机已开启：设置→开发者选项→USB 调试，且 USB 连接模式选择"文件传输(MTP)"。脚本将持续等待...' -ForegroundColor Yellow
            $首次提示已输出 = $true
        }
        # 关键诊断：系统能看到手机（ADB/HDB/MTP 设备存在），但 adb 枚举不到序列号，
        # 这是荣耀/华为 HDB 通道抢占 USB 调试通道的典型特征。
        if (($已识别设备数 -eq 0) -and (-not $HDB劫持提示已输出) -and ($计时器.Elapsed.TotalSeconds -gt 30)) {
            $系统可见手机 = 检测手机USB痕迹
            if ($系统可见手机) {
                Write-Host '' -ForegroundColor Yellow
                Write-Host '  ⚠ 诊断发现：Windows 设备里能看到手机（HDB/ADB 接口存在），但 adb 拿不到设备序列号。' -ForegroundColor Yellow
                Write-Host '    这通常是荣耀/华为手机的 HDB（Honor Debug Bridge）通道占用了 USB 调试通道。' -ForegroundColor Yellow
                Write-Host ("    系统识别到的手机设备：{0}" -f ($系统可见手机 -join '、')) -ForegroundColor DarkYellow
                Write-Host '    请在手机上依次操作：' -ForegroundColor Yellow
                Write-Host '      1. 设置 → 搜索"HDB" → 关闭「允许通过 HDB 连接设备」' -ForegroundColor Yellow
                Write-Host '      2. 开发者选项 → 点击「撤销 USB 调试授权」' -ForegroundColor Yellow
                Write-Host '      3. 开发者选项 → 选择 USB 配置 → 尝试「RNDIS (USB 以太网)」' -ForegroundColor Yellow
                Write-Host '      4. 重新插拔数据线，并在弹窗中勾选"始终允许"' -ForegroundColor Yellow
                Write-Host '    完成后脚本会自动继续检测，无需重跑。' -ForegroundColor Yellow
                Write-Host '' -ForegroundColor Yellow
                $HDB劫持提示已输出 = $true
            }
        }
        Start-Sleep -Seconds 3
    }
    if ($HDB劫持提示已输出) {
        停止并询问 "等待设备超时（$超时秒 秒），期间已提示疑似荣耀 HDB 通道占用但未恢复。请按上方步骤在手机上关闭 HDB 并重新授权后，再运行本脚本。"
    }
    停止并询问 "等待设备超时（$超时秒 秒）。请按屏幕提示检查手机的 USB 调试设置后，重新运行本脚本。"
}

# ============================ 主流程 ============================
try {
    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host '   ImageForge 一键部署（目标：USB 连接的 Android 手机）' -ForegroundColor Cyan
    Write-Host '============================================================' -ForegroundColor Cyan
    $配置表 = 读取配置

    # ---------- 第 0 步：从工作区解析项目信息 ----------
    Write-Host ''
    Write-Host '【0/5】解析工作区项目信息...' -ForegroundColor Cyan
    $解析结果 = 解析Gradle项目信息
    $应用包名     = $解析结果['应用包名']
    $主Activity   = $解析结果['主Activity']
    $最低JDK主版本 = $解析结果['最低JDK']
    $编译Sdk版本   = $解析结果['编译Sdk']
    $最高JDK主版本 = 解析Gradle支持的最高Java版本
    Write-Host "  ✔ 应用包名：$应用包名" -ForegroundColor DarkGray
    Write-Host "  ✔ 主 Activity：$主Activity" -ForegroundColor DarkGray
    Write-Host "  ✔ compileSdk：$编译Sdk版本；最低 JDK：$最低JDK主版本；Gradle 支持的最高 Java 版本：$最高JDK主版本" -ForegroundColor DarkGray

    # Gradle 发行版走镜像且不改动 git 跟踪文件：以仓库 HEAD 中的发行版链接为基准预热缓存；
    # 最后把工作区里 wrapper 配置的临时改动恢复回去。
    $官方发行版链接 = 获取HEAD版发行版链接
    $当前发行版链接 = 解析发行版链接 (Get-Content (Join-Path $项目目录 'gradle\wrapper\gradle-wrapper.properties') -Raw)
    if ($官方发行版链接) {
        Write-Host ''
        Write-Host '   ➤ 准备 Gradle 发行版（镜像下载/缓存预热，不修改 git 跟踪文件）...' -ForegroundColor Cyan
        预热Gradle发行版缓存 -发行版链接 $官方发行版链接
        if ($当前发行版链接 -and ($当前发行版链接 -ne $官方发行版链接)) {
            Write-Host ('  ℹ 当前工作区的 distributionUrl 已被改为镜像源（' + $当前发行版链接 + '）。缓存预热完成后将在脚本末尾恢复为仓库原始内容。') -ForegroundColor DarkYellow
        }
    }

    # ---------- 第 1 步：JDK ----------
    Write-Host ''
    Write-Host '【1/5】检查 JDK...' -ForegroundColor Cyan
    $JDK常见路径 = @()
    $JDK常见路径 += $env:JAVA_HOME
    foreach ($盘符根 in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if (-not $盘符根) { continue }
        $JDK常见路径 += @(Get-ChildItem $盘符根 -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^(Java|Eclipse Adoptium|Microsoft|Zulu|Semeru|Corretto|jdk)' } |
            ForEach-Object { $_.FullName })
        foreach ($子根 in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Zulu')) {
            $子目录 = Join-Path $盘符根 $子根
            if (Test-Path $子目录) {
                $JDK常见路径 += @(Get-ChildItem $子目录 -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -match 'jdk' } | ForEach-Object { $_.FullName })
            }
        }
    }
    $JDK根目录 = 定位依赖 -依赖名称 'JDK (Java 17+)' -配置键名 'JAVA_HOME' -配置表 $配置表 `
        -常见路径列表 $JDK常见路径 -验证器 $JDK验证器 -自动安装器 ${function:自动安装JDK}
    $JDK实际版本 = 获取Java主版本 (Join-Path $JDK根目录 'bin\java.exe')
    Write-Host "  ✔ 使用 JDK 主版本：$JDK实际版本" -ForegroundColor Green

    # ---------- 第 2 步：Android SDK ----------
    Write-Host ''
    Write-Host '【2/5】检查 Android SDK...' -ForegroundColor Cyan
    $Sdk常见路径 = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        'C:\Android\Sdk',
        'C:\AndroidSdk',
        (Join-Path $env:ProgramFiles 'Android\Sdk'),
        (Join-Path ${env:ProgramFiles(x86)} 'Android\android-sdk')
    )
    $Sdk根目录 = 定位依赖 -依赖名称 'Android SDK' -配置键名 'ANDROID_SDK' -配置表 $配置表 `
        -常见路径列表 $Sdk常见路径 -验证器 $Sdk验证器 -自动安装器 ${function:自动安装AndroidSdk}

    # ---------- 第 3 步：统一环境变量，确保 adb 可用 ----------
    Write-Host ''
    Write-Host '【3/5】配置本次进程的构建环境...' -ForegroundColor Cyan
    $env:JAVA_HOME        = $JDK根目录
    $env:ANDROID_HOME     = $Sdk根目录
    $env:ANDROID_SDK_ROOT = $Sdk根目录
    $adb路径 = 定位adb -Sdk根目录 $Sdk根目录
    $env:Path = (Split-Path $adb路径) + ';' + $env:Path
    Write-Host "  ✔ JAVA_HOME    = $JDK根目录"
    Write-Host "  ✔ ANDROID_HOME = $Sdk根目录"
    Write-Host "  ✔ adb          = $adb路径"

    # 通过 gitignored 的 local.properties 显式告知 AGP SDK 位置（该文件已被 .gitignore 覆盖）
    $localProperties路径 = Join-Path $项目目录 'local.properties'
    $sdkDir内容 = "sdk.dir=$($Sdk根目录 -replace '\\', '/')"
    $现有内容 = $null
    if (Test-Path $localProperties路径) { $现有内容 = Get-Content $localProperties路径 -Raw -Encoding UTF8 }
    if ($现有内容 -notmatch [regex]::Escape($sdkDir内容)) {
        Set-Content -Path $localProperties路径 -Value $sdkDir内容 -Encoding UTF8
        Write-Host "  ✔ 已写入 local.properties（该文件在 .gitignore 中，不会进入版本库）"
    }

    # ---------- 第 4 步：等待手机就绪 ----------
    Write-Host ''
    Write-Host '【4/5】检测 USB 连接的 Android 设备...' -ForegroundColor Cyan
    $设备序列号 = 等待Android设备 -adb路径 $adb路径
    Write-Host "  ✔ 设备已就绪：$设备序列号" -ForegroundColor Green

    # ---------- 第 5 步：编译并安装 ----------
    Write-Host ''
    Write-Host '【5/5】编译并安装 ImageForge（首次构建需要下载 Gradle 与依赖，可能耗时较久）...' -ForegroundColor Cyan
    [Console]::OutputEncoding = [Text.Encoding]::UTF8
    # 通过 init script 把 buildToolsVersion 对齐到 compileSdk 版本（不修改项目文件、不安装旧版 build-tools）
    if ($null -ne $编译Sdk版本) {
        $注入脚本路径 = 生成构建注入脚本 -compileSdk版本 $编译Sdk版本
        Write-Host "  ➤ 已生成 buildToolsVersion=${编译Sdk版本}.0.0 的构建注入脚本（init script，不改动项目文件）" -ForegroundColor DarkGray
    }
    else {
        $注入脚本路径 = $null
        Write-Host '  ⚠ 未能解析 compileSdk，跳过 buildToolsVersion 注入（缺组件时仍会自动补装所需 build-tools）' -ForegroundColor Yellow
    }
    # 用 -p 指定项目目录，gradlew.bat 以绝对路径调用（其脚本内部用 %~dp0 自定位），
    # 因此整个构建过程无需切换当前工作目录。
    $gradlew参数 = @('-p', $项目目录, ':app:installDebug', '--console=plain')
    if ($注入脚本路径) { $gradlew参数 = @('-I', $注入脚本路径) + $gradlew参数 }
    # gradlew.bat 内部会执行 cd %APP_HOME%（批处理的目录切换会泄漏回 PowerShell 会话），
    # 因此必须用 cmd 子进程执行并在子进程内还原工作目录，保证调用前后当前目录分毫不变。
    # 用 /v:on 开启延迟变量展开，将 gradlew 的退出码存进 RC 后再还原目录、按原退出码退出。
    $构建工作目录 = (Get-Location).Path
    $gradlew参数转义 = ($gradlew参数 | ForEach-Object { '"' + $_ + '"' }) -join ' '
    $gradlew命令行 = "cd /d `"$构建工作目录`" && `"$项目目录\gradlew.bat`" $gradlew参数转义 & set RC=!ERRORLEVEL! & cd /d `"$构建工作目录`" & exit /b !RC!"
    $最大构建尝试次数 = 2
    $构建尝试次数 = 0
    $构建退出码 = 1
    while ($构建尝试次数 -lt $最大构建尝试次数) {
        $构建尝试次数++
        $构建日志文件 = Join-Path $env:TEMP "imageforge-build-$构建尝试次数.log"
        运行外部命令 -可执行文件 'cmd.exe' -命令参数 @('/d', '/s', '/v:on', '/c', "`"$gradlew命令行`"") | Tee-Object -FilePath $构建日志文件 | ForEach-Object { Write-Host $_ }
        $构建退出码 = $global:LASTEXITCODE
        if ($构建退出码 -eq 0) { break }
        $构建日志内容 = ''
        if (Test-Path $构建日志文件) { $构建日志内容 = Get-Content $构建日志文件 -Raw -Encoding UTF8 }
        # 兜底场景：许可协议未接受或仍有组件缺失。先检查 SDK 是否可写——
        # 只读 SDK（如 Program Files 下）无法自动补装，此时停下来问你。
        if (($构建尝试次数 -lt $最大构建尝试次数) -and ($构建日志内容 -match 'licen[cs]es have not been accepted')) {
            if (-not (测试目录可写 $Sdk根目录)) {
                停止并询问 "构建仍提示 SDK 许可/组件缺失，但当前 SDK（$Sdk根目录）对当前账户只读，无法自动接受许可或补装组件。`n可选处理：由你提供一个可写入的 SDK 路径，或以管理员运行一次 sdkmanager --licenses。"
            }
            $缺失包 = 从构建日志提取缺失SDK包 $构建日志内容
            Write-Host "  ⟳ 构建失败原因：SDK 许可协议未接受 / 缺少组件：$($缺失包 -join ', ')。正在自动补装（不降级任何已装包）..." -ForegroundColor Yellow
            if (-not (安装Sdk组件 -Sdk根目录 $Sdk根目录 -包列表 $缺失包)) {
                停止并询问 "自动补装 SDK 组件失败（可能网络受限或权限不足）。请人工检查后重试。"
            }
            continue
        }
        停止并询问 "Gradle 构建失败（退出码 $构建退出码）。请查看上方输出定位原因；`n如涉及依赖版本冲突/不兼容，按约束脚本不会自动降级任何包，需人工确认。"
    }

    # 验证安装并启动应用
    $已安装检查 = (运行外部命令 -可执行文件 $adb路径 -命令参数 @('-s', $设备序列号, 'shell', 'pm', 'list', 'packages', $应用包名) | Out-String)
    if ($已安装检查 -match $应用包名) {
        Write-Host "  ✔ APK 已成功安装到手机（包名：$应用包名）" -ForegroundColor Green
        运行外部命令 -可执行文件 $adb路径 -命令参数 @('-s', $设备序列号, 'shell', 'am', 'start', '-n', "$应用包名/$主Activity") | Out-Null
        Write-Host '  ✔ 已在手机上启动 ImageForge' -ForegroundColor Green
    }
    else {
        停止并询问 '构建成功，但手机上未检测到已安装的包，请查看上方 Gradle 输出确认安装日志。'
    }

    # 收尾：把工作区里 Gradle wrapper 配置的临时改动恢复为仓库原始内容
    还原被修改的Wrapper配置

    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Green
    Write-Host '   🎉 部署完成！' -ForegroundColor Green
    Write-Host '   （本次运行只在 .gitignore 覆盖的目录与用户目录下产生改动）' -ForegroundColor DarkGray
    Write-Host '============================================================' -ForegroundColor Green
}
catch {
    Write-Host ''
    Write-Host "❌ 部署终止：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
