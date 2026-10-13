$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$base = "http://127.0.0.1:8081"
$redisCli = "D:\Redis-x64-3.2.100\redis-cli.exe"
$out = New-Object System.Collections.Generic.List[string]
function Log([string]$m) { $out.Add($m) }

function Utf8Json {
    param([string]$method, [string]$path, $json, [string]$token)
    $wc = New-Object System.Net.WebClient
    $wc.Encoding = [System.Text.Encoding]::UTF8
    $wc.Headers.Add("Content-Type", "application/json; charset=utf-8")
    if ($token) { $wc.Headers.Add("authorization", $token) }
    try {
        $text = if ($null -eq $json) { $wc.UploadString("$base$path", $method, "") } else { $wc.UploadString("$base$path", $method, $json) }
        return ($text | ConvertFrom-Json)
    } catch { return [pscustomobject]@{ success = $false; errorMsg = ("HTTP_ERR: " + $_.Exception.Message) } }
}
function Utf8Get([string]$path, [string]$token) {
    $wc = New-Object System.Net.WebClient
    $wc.Encoding = [System.Text.Encoding]::UTF8
    if ($token) { $wc.Headers.Add("authorization", $token) }
    try { return (($wc.DownloadString("$base$path")) | ConvertFrom-Json) }
    catch { return [pscustomobject]@{ success = $false; errorMsg = ("HTTP_ERR: " + $_.Exception.Message) } }
}
function Show([string]$label, $r, [string]$detail) {
    $ok = if ($r -and $r.success) { "OK " } else { "FAIL" }
    Log ("[$ok] $label$detail")
}

# 登录
$c1 = Utf8Json 'POST' "/user/code?phone=19999999999" "{}" $null
$t1 = (Utf8Json 'POST' "/user/login" (@{ phone = '19999999999'; code = "$($c1.data)" } | ConvertTo-Json)).data
$c2 = Utf8Json 'POST' "/user/code?phone=18888888888" "{}" $null
$t2 = (Utf8Json 'POST' "/user/login" (@{ phone = '18888888888'; code = "$($c2.data)" } | ConvertTo-Json)).data
Log ("[$(if ($t1 -and $t2) {'OK '} else {'FAIL'})] 登录 1011(作者) / 1012(粉丝)")
Log ""
Log "===== 同 score 冲突下的 scroll 分页（原 bug 的精确复现场景） ====="

# 1) 发布 12 条真实笔记
$made = New-Object System.Collections.Generic.List[long]
for ($i = 1; $i -le 12; $i++) {
    $r = Utf8Json 'POST' "/blog" (@{
        title = "同分页测试 $i"
        content = "12 条笔记使用完全相同的 score，用于验证 offset 计算是否正确。"
        images = "/imgs/blogs/7/14/tie-$i.jpg"
        topicId = 1
    } | ConvertTo-Json) $t1
    if ($r.success) { $made.Add([long]$r.data) }
}
Log ("       发布 " + $made.Count + " 条笔记: " + ($made -join ', '))

# 2) 把它们在 1012 的收件箱里改成完全相同的 score
$feedKey = "feed:1012"
$sameScore = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$zaddArgs = @("-a", "123456", "ZADD", $feedKey)
foreach ($id in $made) { $zaddArgs += @("$sameScore", "$id") }
$zaddRes = & $redisCli @zaddArgs 2>$null
Log ("       ZADD " + $feedKey + " 全部 score=" + $sameScore + " -> " + ($zaddRes -join ' '))
$card = (& $redisCli -a 123456 ZCARD $feedKey 2>$null) -join ''
Log ("       收件箱总条数 = " + $card)
$topScores = (& $redisCli -a 123456 ZREVRANGE $feedKey 0 2 WITHSCORES 2>$null) -join ' '
Log ("       收件箱前三(含score) = " + $topScores)

# 3) 逐页翻，检查不丢不重
$seen = New-Object System.Collections.Generic.List[long]
$dupes = 0
$max = 0; $off = 0; $pages = 0
$page1Offset = -1
$page1MinTime = -1
while ($pages -lt 15) {
    $f = Utf8Get "/blog/of/follow?lastId=$max&offset=$off" $t2
    $cnt = if ($f.data.list) { @($f.data.list).Count } else { 0 }
    if ($pages -eq 0) { $page1Offset = $f.data.offset; $page1MinTime = $f.data.minTime }
    Log ("       第 " + ($pages + 1) + " 页: " + $cnt + " 条 (minTime=" + $f.data.minTime + ", offset=" + $f.data.offset + ")")
    if ($cnt -eq 0) { break }
    foreach ($b in $f.data.list) {
        if ($seen.Contains([long]$b.id)) { $dupes++ }
        $seen.Add([long]$b.id)
    }
    $max = $f.data.minTime; $off = $f.data.offset; $pages++
}
$missing = New-Object System.Collections.Generic.List[long]
foreach ($id in $made) { if (-not $seen.Contains($id)) { $missing.Add($id) } }
$ok = if ($dupes -eq 0 -and $missing.Count -eq 0 -and $page1Offset -eq 10 -and [long]$page1MinTime -eq [long]$sameScore) { "OK  " } else { "FAIL" }
Log ("[$ok] 12 条同 score 笔记全部翻到、无重复，且首页 offset 正确 :: 累计=" + $seen.Count + " 重复=" + $dupes + " 缺失=" + $missing.Count)
Log ("       首页 minTime=" + $page1MinTime + " (期望 " + $sameScore + "), 首页 offset=" + $page1Offset + " (期望 10)")
Log ("       -> 这正是不修 offset 就必然出现重复/丢数据的场景")

# 清理
foreach ($id in $made) { Utf8Json 'DELETE' "/blog/$id" $null $t1 | Out-Null }
Log ("       已清理 " + $made.Count + " 条测试笔记")

$fail = ($out | Where-Object { $_ -match '^\[FAIL' }).Count
$okc = ($out | Where-Object { $_ -match '^\[OK' }).Count
Log ""
Log "===== 汇总: OK=$okc FAIL=$fail ====="
[System.IO.File]::WriteAllLines("D:\heima redis\实战篇\后端项目-初始状态\tools\verify-feed-scroll-same-score.result.txt", $out, [System.Text.UTF8Encoding]::new($false))
Write-Host "verify4 done: OK=$okc FAIL=$fail"
