$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$base = "http://127.0.0.1:8081"
$out = New-Object System.Collections.Generic.List[string]
function Log([string]$m) { $out.Add($m) }

function Utf8Json {
    param([string]$method, [string]$path, $json, [string]$token)
    $wc = New-Object System.Net.WebClient
    $wc.Encoding = [System.Text.Encoding]::UTF8
    $wc.Headers.Add("Content-Type", "application/json; charset=utf-8")
    if ($token) { $wc.Headers.Add("authorization", $token) }
    try {
        if ($null -eq $json) {
            # 无请求体也要用指定方法，避免退化成 GET
            $text = $wc.UploadString("$base$path", $method, "")
        } else {
            $text = $wc.UploadString("$base$path", $method, $json)
        }
        return ($text | ConvertFrom-Json)
    } catch {
        return [pscustomobject]@{ success = $false; errorMsg = ("HTTP_ERR: " + $_.Exception.Message) }
    }
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
    $msg = if ($r -and $r.success) { "" } else { " :: " + $r.errorMsg }
    Log ("[$ok] $label$msg$detail")
}

# ---------------- 登录
$c1 = Utf8Json 'POST' "/user/code?phone=19999999999" "{}" $null
$login = Utf8Json 'POST' "/user/login" (@{ phone = '19999999999'; code = "$($c1.data)" } | ConvertTo-Json) $null
$t1 = $login.data
$c2 = Utf8Json 'POST' "/user/code?phone=18888888888" "{}" $null
$t2 = (Utf8Json 'POST' "/user/login" (@{ phone = '18888888888'; code = "$($c2.data)" } | ConvertTo-Json)).data
$ready = if ($t1 -and $t2) { "OK " } else { "FAIL" }
Log "[$ready] 登录 1011(作者) / 1012(粉丝)"

# ---------------- 收藏 toggle 正确性
Log ""
Log "===== 1. 收藏 toggle（修正测试脚本的 PUT 无体问题） ====="
$d0 = Utf8Get "/blog/23" $t1
$baseCol = [int]$d0.data.collected
$isCol0 = [bool]$d0.data.isCollected
Log ("       初始: collected=" + $baseCol + " isCollected=" + $isCol0)
$r1 = Utf8Json 'PUT' "/blog/collect/23" $null $t1
Show "PUT /blog/collect/23 (第一次)" $r1 (" :: collected=" + $r1.data.collected + " isCollected=" + $r1.data.isCollected)
$d1 = Utf8Get "/blog/23" $t1
$c1ok = if ([int]$d1.data.collected -eq [int]$r1.data.collected -and $d1.data.isCollected -eq $r1.data.isCollected) { "OK  " } else { "FAIL" }
Log ("[$c1ok] 详情与 toggle 返回一致 :: collected=" + $d1.data.collected + " isCollected=" + $d1.data.isCollected)
$r2 = Utf8Json 'PUT' "/blog/collect/23" $null $t1
Show "PUT /blog/collect/23 (再次=取消)" $r2 (" :: collected=" + $r2.data.collected + " isCollected=" + $r2.data.isCollected)
$d2 = Utf8Get "/blog/23" $t1
$restore = if ([int]$d2.data.collected -eq $baseCol) { "OK  " } else { "FAIL" }
Log ("[$restore] 两次 toggle 后回到初始计数 :: " + $d2.data.collected + " (期望 " + $baseCol + ")")
$myCol = Utf8Get "/blog/of/collect" $t1
Show "GET /blog/of/collect" $myCol (" :: " + @($myCol.data).Count + " 条")

# 点赞 toggle
$d0l = [int](Utf8Get "/blog/23" $t1).data.liked
$l1 = Utf8Json 'PUT' "/blog/like/23" $null $t1
Show "PUT /blog/like/23 (第一次)" $l1 (" :: liked=" + $l1.data.liked + " isLike=" + $l1.data.isLike)
$l2 = Utf8Json 'PUT' "/blog/like/23" $null $t1
Show "PUT /blog/like/23 (再次=取消)" $l2 (" :: liked=" + $l2.data.liked + " isLike=" + $l2.data.isLike)
$d2l = [int](Utf8Get "/blog/23" $t1).data.liked
$lRestore = if ($d2l -eq $d0l) { "OK  " } else { "FAIL" }
Log ("[$lRestore] 两次 toggle 后点赞数回到初始 :: " + $d2l + " (期望 " + $d0l + ")")
$myLik = Utf8Get "/blog/of/liked" $t1
Show "GET /blog/of/liked" $myLik (" :: " + @($myLik.data).Count + " 条")

# ---------------- Feed 流跨页翻页（关键回归）
Log ""
Log "===== 2. 关注 Feed 流跨页翻页（原来 offset 计算是坏的） ====="
$beforePages = 0
$max = 0; $off = 0
while ($beforePages -lt 8) {
    $f = Utf8Get "/blog/of/follow?lastId=$max&offset=$off" $t2
    $cnt = if ($f.data.list) { @($f.data.list).Count } else { 0 }
    if ($cnt -eq 0) { break }
    $max = $f.data.minTime; $off = $f.data.offset; $beforePages++
}
Log ("       发布前粉丝收件箱共 " + $beforePages + " 页（每页 10）")

$made = New-Object System.Collections.Generic.List[long]
Log "       正在发布 15 条测试笔记（连续发布，会产生大量同毫秒 score，正好压测 offset 逻辑）..."
for ($i = 1; $i -le 15; $i++) {
    $r = Utf8Json 'POST' "/blog" (@{
        title = "跨页翻页测试 $i"
        content = "用于验证 scroll 分页在同毫秒 score 下不丢不重。"
        images = "/imgs/blogs/7/14/page-test-$i.jpg"
        topicId = 1
        location = "拱墅区"
    } | ConvertTo-Json) $t1
    if ($r.success) { $made.Add([long]$r.data) }
}
Log ("       成功发布 " + $made.Count + " 条: " + ($made -join ', '))

$seen = New-Object System.Collections.Generic.List[long]
$dupes = 0
$max = 0; $off = 0; $pages = 0
while ($pages -lt 10) {
    $f = Utf8Get "/blog/of/follow?lastId=$max&offset=$off" $t2
    $cnt = if ($f.data.list) { @($f.data.list).Count } else { 0 }
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
$pageOk = if ($dupes -eq 0 -and $missing.Count -eq 0 -and $pages -ge 2) { "OK  " } else { "FAIL" }
Log ("[$pageOk] 跨页翻页正确 :: 页数=" + $pages + " 累计=" + $seen.Count + " 重复=" + $dupes + " 缺失=" + $missing.Count)

# 清理
foreach ($id in $made) { Utf8Json 'DELETE' "/blog/$id" $null $t1 | Out-Null }
Log ("       已清理 " + $made.Count + " 条测试笔记")

# ---------------- 删除后收件箱应清理
Log ""
Log "===== 3. 删除笔记后粉丝收件箱同步清理 ====="
$tmp = Utf8Json 'POST' "/blog" (@{
    title = "删除同步测试"; content = "发布后立刻删除，收件箱里也不应该再出现。"
    images = "/imgs/blogs/7/14/del-test.jpg"; topicId = 1
} | ConvertTo-Json) $t1
$tmpId = $tmp.data
$feedAfterPost = Utf8Get "/blog/of/follow?lastId=0&offset=0" $t2
$presentBefore = $false
foreach ($b in $feedAfterPost.data.list) { if ([long]$b.id -eq [long]$tmpId) { $presentBefore = $true } }
Log ("       发布后出现在收件箱: " + $presentBefore)
Utf8Json 'DELETE' "/blog/$tmpId" $null $t1 | Out-Null
$feedAfterDel = Utf8Get "/blog/of/follow?lastId=0&offset=0" $t2
$presentAfter = $false
foreach ($b in $feedAfterDel.data.list) { if ([long]$b.id -eq [long]$tmpId) { $presentAfter = $true } }
$delOk = if ($presentBefore -and -not $presentAfter) { "OK  " } else { "FAIL" }
Log ("[$delOk] 删除后不再出现在收件箱: " + $presentAfter)

$fail = ($out | Where-Object { $_ -match '^\[FAIL' }).Count
$okc = ($out | Where-Object { $_ -match '^\[OK' }).Count
Log ""
Log "===== 汇总: OK=$okc FAIL=$fail ====="
[System.IO.File]::WriteAllLines("D:\heima redis\实战篇\后端项目-初始状态\tools\verify-feed-scroll.result.txt", $out, [System.Text.UTF8Encoding]::new($false))
Write-Host "verify3 done: OK=$okc FAIL=$fail"
