$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$base = "http://127.0.0.1:8081"
$out = New-Object System.Collections.Generic.List[string]
function Log([string]$m) { $out.Add($m) }

# 用 UTF-8 明确解码的请求，验证中文往返
function Utf8Json([string]$method, [string]$path, [string]$json, [string]$token) {
    $wc = New-Object System.Net.WebClient
    $wc.Encoding = [System.Text.Encoding]::UTF8
    $wc.Headers.Add("Content-Type", "application/json; charset=utf-8")
    if ($token) { $wc.Headers.Add("authorization", $token) }
    try {
        $text = if ($json) { $wc.UploadString("$base$path", $method, $json) } else { $wc.DownloadString("$base$path") }
        return ($text | ConvertFrom-Json)
    } catch {
        return [pscustomobject]@{ success = $false; errorMsg = ("HTTP_ERR: " + $_.Exception.Message) }
    }
}

function Api {
    param([string]$method, [string]$path, $bodyJson, [string]$token)
    $headers = @{}
    if ($token) { $headers["authorization"] = $token }
    $a = @{ Uri = "$base$path"; Method = $method; Headers = $headers; TimeoutSec = 25; UseBasicParsing = $true }
    if ($bodyJson) {
        $a["Body"] = [System.Text.Encoding]::UTF8.GetBytes($bodyJson)
        $a["ContentType"] = 'application/json; charset=utf-8'
    }
    try { return Invoke-RestMethod @a }
    catch { return [pscustomobject]@{ success = $false; errorMsg = ("HTTP_ERR: " + $_.Exception.Message) } }
}

function Show([string]$label, $r, [string]$detail) {
    $ok = if ($r -and $r.success) { "OK " } else { "FAIL" }
    $msg = if ($r -and $r.success) { "" } else { " :: " + $r.errorMsg }
    Log ("[$ok] $label$msg$detail")
}
function Count-Of($r) {
    if (-not $r -or -not $r.success -or $null -eq $r.data) { return 0 }
    if ($r.data -is [System.Array]) { return $r.data.Count }
    return 1
}

# ---------------- 登录
$c1 = Api 'POST' "/user/code?phone=19999999999"
$t1 = (Api 'POST' "/user/login" (@{ phone = '19999999999'; code = "$($c1.data)" } | ConvertTo-Json)).data
$c2 = Api 'POST' "/user/code?phone=18888888888"
$t2 = (Api 'POST' "/user/login" (@{ phone = '18888888888'; code = "$($c2.data)" } | ConvertTo-Json)).data
$ready = if ($t1 -and $t2) { "OK " } else { "FAIL" }
Log "[$ready] 登录 1011 / 1012"
Log ""

# ---------------- 1) 之前失败的关注列表接口
Log "===== 1. 关注 / 粉丝列表（原来 401） ====="
$fl = Api 'GET' "/follow/list/1011"
Show "GET /follow/list/1011" $fl (" :: " + (Count-Of $fl) + " 人")
if ((Count-Of $fl) -gt 0) { Log ("       " + $fl.data[0].nickName + " / " + $fl.data[0].city) }
$fs = Api 'GET' "/follow/fans/1011"
Show "GET /follow/fans/1011" $fs (" :: " + (Count-Of $fs) + " 人")
$ornot = Api 'GET' "/follow/or/not/1011"
Show "GET /follow/or/not/1011 (匿名)" $ornot (" :: " + $ornot.data)
Log ""

# ---------------- 2) 中文 UTF-8 往返
Log "===== 2. 中文 UTF-8 往返 ====="
$cn = "中文往返测试：环境安静，手冲很稳，推荐给喜欢咖啡的朋友 ☕"
$posted = Utf8Json 'POST' "/blog-comments" (@{ blogId = 23; content = $cn } | ConvertTo-Json) $t1
Show "POST /blog-comments (UTF-8 中文)" $posted (" :: id=" + $posted.data)

$listJson = Utf8Json 'GET' "/blog-comments/of/blog/23?current=1" $null $t1
$hit = $null
foreach ($c in $listJson.data) { if ($c.id -eq $posted.data) { $hit = $c } }
$match = if ($hit -and $hit.content -eq $cn) { "OK  " } else { "FAIL" }
Log ("[$match] 中文评论往返一致 :: 原文=" + $cn)
if ($hit) { Log ("       读回=" + $hit.content) } else { Log "       未找到刚发布的评论" }

$cnBlog = Utf8Json 'POST' "/blog" (@{
    title = "中文笔记标题：运河边的宝藏咖啡店"
    content = "环境安静，手冲很稳，适合坐一下午。评分 4.8！"
    images = "/imgs/blogs/7/14/4771fefb-1a87-4d6a-9d3f-000000000002.jpg"
    topicId = 4
    shopId = 4
    score = 48
    location = "拱宸桥/上塘"
} | ConvertTo-Json) $t1
Show "POST /blog (UTF-8 中文笔记)" $cnBlog (" :: id=" + $cnBlog.data)
$nb = $cnBlog.data
$detail = Utf8Json 'GET' "/blog/$nb" $null $t1
$titleOk = if ($detail.data.title -eq "中文笔记标题：运河边的宝藏咖啡店") { "OK  " } else { "FAIL" }
Log ("[$titleOk] 中文标题往返一致 :: " + $detail.data.title)
Log ("       话题=" + $detail.data.topicName + " 商户=" + $detail.data.shopName + " 评分=" + $detail.data.score)
$s = Utf8Json 'GET' "/blog/search?keyword=%E5%92%96%E5%95%A1" $null $t1
Show "GET /blog/search?keyword=咖啡" $s (" :: " + (Count-Of $s) + " 条")
$s2 = Utf8Json 'GET' "/blog/search?keyword=%E6%9D%AD%E5%B7%9E" $null $t1
Show "GET /blog/search?keyword=杭州" $s2 (" :: " + (Count-Of $s2) + " 条")
$s3 = Utf8Json 'GET' "/shop/search?keyword=%E7%81%AB%E9%94%85" $null $t1
Show "GET /shop/search?keyword=火锅" $s3 (" :: " + (Count-Of $s3) + " 家")
if ((Count-Of $s3) -gt 0) { Log ("       " + $s3.data[0].name) }
# 清理测试笔记
Utf8Json 'DELETE' "/blog/$nb" $null $t1 | Out-Null
Log ""

# ---------------- 3) Feed 流翻页正确性（构造同毫秒多条）
Log "===== 3. 关注 Feed 流 scroll 翻页 ====="
$c3 = Api 'POST' "/user/code?phone=18888888888"
$t2 = (Api 'POST' "/user/login" (@{ phone = '18888888888'; code = "$($c3.data)" } | ConvertTo-Json)).data
Api 'PUT' "/follow/1011/true" $null $t2 | Out-Null
$ids = @()
for ($i = 1; $i -le 3; $i++) {
    $r = Utf8Json 'POST' "/blog" (@{
        title = "翻页测试笔记 $i"
        content = "用于验证 scroll 分页不会丢数据也不会重复。"
        images = "/imgs/blogs/7/14/4771fefb-1a87-4d6a-9d3f-00000000000$i.jpg"
        topicId = 1
    } | ConvertTo-Json) $t1
    $ids += $r.data
}
Log ("       已发布 3 条测试笔记: " + ($ids -join ', '))

$seen = New-Object System.Collections.Generic.List[long]
$max = 0; $off = 0; $pages = 0; $dupes = 0
while ($pages -lt 6) {
    $f = Utf8Json 'GET' "/blog/of/follow?lastId=$max&offset=$off" $null $t2
    $cnt = 0
    if ($f.data.list) { $cnt = @($f.data.list).Count }
    Log ("       第 " + ($pages + 1) + " 页: " + $cnt + " 条 (minTime=" + $f.data.minTime + ", offset=" + $f.data.offset + ")")
    if ($cnt -eq 0) { break }
    foreach ($b in $f.data.list) {
        if ($seen.Contains([long]$b.id)) { $dupes++ }
        $seen.Add([long]$b.id)
    }
    $max = $f.data.minTime; $off = $f.data.offset; $pages++
}
$allFound = $true
foreach ($id in $ids) { if (-not $seen.Contains([long]$id)) { $allFound = $false } }
$pagingOk = if ($dupes -eq 0 -and $allFound) { "OK  " } else { "FAIL" }
Log ("[$pagingOk] 翻页不重复且不丢数据 :: 共 " + $seen.Count + " 条, 重复 " + $dupes + ", 3 条测试笔记全部出现=" + $allFound)
foreach ($id in $ids) { Utf8Json 'DELETE' "/blog/$id" $null $t1 | Out-Null }
Log "       测试笔记已清理"
Log ""

# ---------------- 4) 收藏 / 点赞 计数一致性
Log "===== 4. 计数一致性 ====="
$before = Utf8Json 'GET' "/blog/23" $null $t1
$c = Utf8Json 'PUT' "/blog/collect/23" $null $t1
$after = Utf8Json 'GET' "/blog/23" $null $t1
$colOk = if ($after.data.collected -eq $c.data.collected) { "OK  " } else { "FAIL" }
Log ("[$colOk] 收藏后 collected 一致 :: toggle=" + $c.data.collected + " detail=" + $after.data.collected + " isCollected=" + $after.data.isCollected)
# 还原
Utf8Json 'PUT' "/blog/collect/23" $null $t1 | Out-Null
Log ""

$fail = ($out | Where-Object { $_ -match '^\[FAIL' }).Count
$okc = ($out | Where-Object { $_ -match '^\[OK' }).Count
Log "===== 汇总: OK=$okc FAIL=$fail ====="
[System.IO.File]::WriteAllLines("D:\heima redis\实战篇\后端项目-初始状态\tools\verify-utf8-and-follow.result.txt", $out, [System.Text.UTF8Encoding]::new($false))
Write-Host "verify2 done: OK=$okc FAIL=$fail"
