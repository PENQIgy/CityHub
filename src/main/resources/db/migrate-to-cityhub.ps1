<#
=====================================================================
 CityHub 数据库迁移脚本
 把旧的 hmdp 库完整克隆为 cityhub，并套用 CityHub 表结构升级 + 演示数据

 用法（在项目根目录或本目录下执行）：
   powershell -ExecutionPolicy Bypass -File migrate-to-cityhub.ps1
   powershell -ExecutionPolicy Bypass -File migrate-to-cityhub.ps1 -MySqlHome "C:\Program Files\MySQL\MySQL Server 8.0" -User root -Password 123456

 说明：
   - 原 hmdp 库不会被删除，作为回滚备份保留
   - cityhub_upgrade.sql / cityhub_seed.sql 均可重复执行（幂等）
=====================================================================
#>
param(
    [string]$MySqlHome = "C:\Program Files\MySQL\MySQL Server 8.0",
    [string]$User = "root",
    [string]$Password = "123456",
    [string]$SourceDb = "hmdp",
    [string]$TargetDb = "cityhub"
)

$ErrorActionPreference = "Stop"
$mysql = Join-Path $MySqlHome "bin\mysql.exe"
$mysqldump = Join-Path $MySqlHome "bin\mysqldump.exe"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

if (-not (Test-Path $mysql)) { throw "找不到 mysql 客户端：$mysql" }
if (-not (Test-Path $mysqldump)) { throw "找不到 mysqldump：$mysqldump" }

$env:MYSQL_PWD = $Password
$baseDump = Join-Path $env:TEMP "$SourceDb`_base.sql"

Write-Host "1/4 导出 $SourceDb ..." -ForegroundColor Cyan
& $mysqldump "-u$User" --default-character-set=utf8mb4 --single-transaction --skip-comments "--result-file=$baseDump" $SourceDb 2>$null
if ($LASTEXITCODE -ne 0) { throw "mysqldump 失败，请确认 $SourceDb 库存在且账号密码正确" }
Write-Host ("     导出完成，" + (Get-Item $baseDump).Length + " 字节")

Write-Host "2/4 重建 $TargetDb ..." -ForegroundColor Cyan
& $mysql "-u$User" -e "DROP DATABASE IF EXISTS ``$TargetDb``; CREATE DATABASE ``$TargetDb`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" 2>$null
if ($LASTEXITCODE -ne 0) { throw "创建 $TargetDb 失败" }

Write-Host "3/4 导入数据 ..." -ForegroundColor Cyan
$p = Start-Process -FilePath $mysql -ArgumentList @("-u$User", "--default-character-set=utf8mb4", $TargetDb) `
    -RedirectStandardInput $baseDump -NoNewWindow -Wait -PassThru
if ($p.ExitCode -ne 0) { throw "导入 $TargetDb 失败" }

function Invoke-SqlFile([string]$file) {
    if (-not (Test-Path $file)) { throw "找不到 SQL 文件：$file" }
    $proc = Start-Process -FilePath $mysql -ArgumentList @("-u$User", "--default-character-set=utf8mb4", $TargetDb) `
        -RedirectStandardInput $file -NoNewWindow -Wait -PassThru
    if ($proc.ExitCode -ne 0) { throw "执行失败：$file" }
}

Write-Host "4/4 套用 CityHub 表结构升级 + 演示数据 ..." -ForegroundColor Cyan
Invoke-SqlFile (Join-Path $scriptDir "cityhub_upgrade.sql")
Invoke-SqlFile (Join-Path $scriptDir "cityhub_seed.sql")

Write-Host ""
Write-Host "迁移完成！" -ForegroundColor Green
& $mysql "-u$User" --default-character-set=utf8mb4 -e @"
SELECT '$TargetDb' AS db,
       (SELECT COUNT(*) FROM tb_blog)     AS blogs,
       (SELECT COUNT(*) FROM tb_topic)    AS topics,
       (SELECT COUNT(*) FROM tb_blog_comments) AS comments,
       (SELECT COUNT(*) FROM tb_blog_collect)  AS collects,
       (SELECT COUNT(*) FROM tb_voucher_order) AS orders;
"@
Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
