$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$base = "http://127.0.0.1:8081"
$out = New-Object System.Collections.Generic.List[string]

function Log([string]$m) { $out.Add($m) }

function Api {
    param([string]$method, [string]$path, $bodyJson, [string]$token, [string]$ct = 'application/json')
    $headers = @{}
    if ($token) { $headers["authorization"] = $token }
    $a = @{ Uri = "$base$path"; Method = $method; Headers = $headers; TimeoutSec = 25; UseBasicParsing = $true }
    if ($bodyJson) { $a["Body"] = $bodyJson; $a["ContentType"] = $ct }
    try {
        return Invoke-RestMethod @a
    } catch {
        return [pscustomobject]@{ success = $false; errorMsg = ("HTTP_ERR: " + $_.Exception.Message) }
    }
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

# ---------------------------------------------------------------- 登录
Log "===== 登录 / 鉴权 ====="

$code1 = Api 'POST' "/user/code?phone=19999999999"
Show "POST /user/code" $code1 (" :: code=" + $code1.data)

$login1 = Api 'POST' "/user/login" (@{ phone = '19999999999'; code = "$($code1.data)" } | ConvertTo-Json)
$token1 = $login1.data
Show "POST /user/login (1011)" $login1 (" :: token=" + (if ($token1) { $token1.Substring(0,8) + "..." } else { "<none>" }))

$code2 = Api 'POST' "/user/code?phone=18888888888"
$login2 = Api 'POST' "/user/login" (@{ phone = '18888888888'; code = "$($code2.data)" } | ConvertTo-Json)
$token2 = $login2.data
Show "POST /user/login (1012)" $login2 ""

$me = Api 'GET' "/user/me" $null $token1
Show "GET /user/me" $me (" :: id=" + $me.data.id + " nick=" + $me.data.nickName)

$noAuth = Api 'GET' "/blog/of/me"
$noAuthOk = if ($noAuth -and -not $noAuth.success) { "OK  " } else { "FAIL" }
Log ("[$noAuthOk] GET /blog/of/me 未登录应被拒绝 :: " + $noAuth.errorMsg)

$logoutProbe = Api 'GET' "/blog/hot"
Show "GET /blog/hot 匿名可访问" $logoutProbe (" :: " + (Count-Of $logoutProbe) + " 条")

# ---------------------------------------------------------------- 内容基础
Log ""
Log "===== 探店笔记 / Feed 流 ====="
$hot = Api 'GET' "/blog/hot?current=1";            Show "GET /blog/hot" $hot (" :: " + (Count-Of $hot) + " 条")
$rec = Api 'GET' "/blog/feed/recommend?current=1"; Show "GET /blog/feed/recommend" $rec (" :: " + (Count-Of $rec) + " 条")
$near = Api 'GET' "/blog/feed/nearby?current=1&x=120.149192&y=30.316078"; Show "GET /blog/feed/nearby" $near (" :: " + (Count-Of $near) + " 条")
$near2 = Api 'GET' "/blog/feed/nearby?current=1";  Show "GET /blog/feed/nearby(无定位)" $near2 (" :: " + (Count-Of $near2) + " 条")
$detail = Api 'GET' "/blog/23";                    Show "GET /blog/23 详情" $detail (" :: title=" + $detail.data.title + " view=" + $detail.data.viewCount + " topic=" + $detail.data.topicName + " shop=" + $detail.data.shopName)
$search = Api 'GET' "/blog/search?keyword=%E5%92%96%E5%95%A1";  Show "GET /blog/search?keyword=咖啡" $search (" :: " + (Count-Of $search) + " 条")
$mine = Api 'GET' "/blog/of/me" $null $token1;     Show "GET /blog/of/me" $mine (" :: " + (Count-Of $mine) + " 条")
$ofUser = Api 'GET' "/blog/of/user?id=1011";       Show "GET /blog/of/user" $ofUser (" :: " + (Count-Of $ofUser) + " 条")

# 话题
$topics = Api 'GET' "/topic/list";                 Show "GET /topic/list" $topics (" :: " + (Count-Of $topics) + " 个")
$topicBlogs = Api 'GET' "/topic/1"
Show "GET /topic/1" $topicBlogs (" :: blogCount=" + $topicBlogs.data.blogCount)
$blogsOfTopic = Api 'GET' "/blog/of/topic?topicId=1"; Show "GET /blog/of/topic" $blogsOfTopic (" :: " + (Count-Of $blogsOfTopic) + " 条")

# 点赞 / 收藏
$like = Api 'PUT' "/blog/like/23" $null $token1
Show "PUT /blog/like/23" $like (" :: liked=" + $like.data.liked + " isLike=" + $like.data.isLike)
$like2 = Api 'PUT' "/blog/like/23" $null $token1
Show "PUT /blog/like/23 (再次=取消)" $like2 (" :: liked=" + $like2.data.liked + " isLike=" + $like2.data.isLike)
$like3 = Api 'PUT' "/blog/like/23" $null $token1
Show "PUT /blog/like/23 (恢复)" $like3 (" :: liked=" + $like3.data.liked + " isLike=" + $like3.data.isLike)

$col = Api 'PUT' "/blog/collect/23" $null $token1
Show "PUT /blog/collect/23" $col (" :: collected=" + $col.data.collected + " isCollected=" + $col.data.isCollected)
$myCol = Api 'GET' "/blog/of/collect" $null $token1; Show "GET /blog/of/collect" $myCol (" :: " + (Count-Of $myCol) + " 条")
$myLike = Api 'GET' "/blog/of/liked" $null $token1;  Show "GET /blog/of/liked" $myLike (" :: " + (Count-Of $myLike) + " 条")
$likes = Api 'GET' "/blog/likes/23";                 Show "GET /blog/likes/23" $likes (" :: " + (Count-Of $likes) + " 人")

# ---------------------------------------------------------------- 评论
Log ""
Log "===== 评论系统 ====="
$cmts = Api 'GET' "/blog-comments/of/blog/23?current=1"
Show "GET /blog-comments/of/blog/23" $cmts (" :: " + (Count-Of $cmts) + " 条一级评论")
if ((Count-Of $cmts) -gt 0) {
    $first = $cmts.data[0]
    Log ("       首条: " + $first.name + " -> " + $first.content + " (回复数 " + $first.replyCount + ", 作者?" + $first.isAuthor + ")")
    if ($first.children -and $first.children.Count -gt 0) {
        Log ("       回复: " + $first.children[0].name + " -> " + $first.children[0].content + " (回复@" + $first.children[0].answerName + ")")
    }
}
$cntBefore = Api 'GET' "/blog-comments/count/23"
$newC = Api 'POST' "/blog-comments" (@{ blogId = 23; content = "联调测试评论：这家店真的很值得去！" } | ConvertTo-Json) $token1
Show "POST /blog-comments" $newC (" :: id=" + $newC.data)
$cntAfter = Api 'GET' "/blog-comments/count/23"
Show "GET /blog-comments/count/23" $cntAfter (" :: " + $cntBefore.data + " -> " + $cntAfter.data)

$reply = Api 'POST' "/blog-comments" (@{ blogId = 23; content = "同意！我上周也去了"; parentId = $newC.data; answerId = $newC.data } | ConvertTo-Json) $token2
Show "POST /blog-comments (二级回复)" $reply (" :: id=" + $reply.data)
$cmts2 = Api 'GET' "/blog-comments/of/blog/23?current=1"
$found = $false
foreach ($c in $cmts2.data) { if ($c.id -eq $newC.data) { $found = $true; Log ("       新评论已出现在列表: 回复数=" + $c.replyCount + " 预览=" + $c.children.Count) } }
if (-not $found) { Log "       [WARN] 新评论未出现在第一页（可能排序在后）" }
$cLike = Api 'PUT' "/blog-comments/like/$($newC.data)" $null $token2
Show "PUT /blog-comments/like/{id}" $cLike (" :: liked=" + $cLike.data.liked)
$cDel = Api 'DELETE' "/blog-comments/$($reply.data)" $null $token2
Show "DELETE /blog-comments/{id}" $cDel ""

# ---------------------------------------------------------------- 用户关系
Log ""
Log "===== 用户关系 / 主页 ====="
$isF = Api 'GET' "/follow/or/not/1011" $null $token2; Show "GET /follow/or/not/1011" $isF (" :: " + $isF.data)
$doF = Api 'PUT' "/follow/1011/true" $null $token2;   Show "PUT /follow/1011/true" $doF ""
$isF2 = Api 'GET' "/follow/or/not/1011" $null $token2
$expect = if ($isF2.data -eq $true) { "OK  " } else { "FAIL" }
Log ("[$expect] 关注后 isFollow 应为 true :: " + $isF2.data)
$unf = Api 'PUT' "/follow/1011/false" $null $token2;  Show "PUT /follow/1011/false" $unf ""
$isF3 = Api 'GET' "/follow/or/not/1011" $null $token2
$expect3 = if ($isF3.data -eq $false) { "OK  " } else { "FAIL" }
Log ("[$expect3] 取关后 isFollow 应为 false :: " + $isF3.data)

$followList = Api 'GET' "/follow/list/1011"; Show "GET /follow/list/1011" $followList (" :: " + (Count-Of $followList) + " 人")
$fansList = Api 'GET' "/follow/fans/1011";   Show "GET /follow/fans/1011" $fansList (" :: " + (Count-Of $fansList) + " 人")
$prof = Api 'GET' "/user/profile/1011"
Show "GET /user/profile/1011" $prof (" :: " + $prof.data.nickName + " 笔记=" + $prof.data.stats.blogCount + " 粉丝=" + $prof.data.stats.fans + " 获赞=" + $prof.data.stats.likedCount)
$stats = Api 'GET' "/user/stats/1011"
Show "GET /user/stats/1011" $stats (" :: blog=" + $stats.data.blogCount + " followee=" + $stats.data.followee + " fans=" + $stats.data.fans + " liked=" + $stats.data.likedCount + " collected=" + $stats.data.collectedCount)

# 发笔记 -> 关注流
Log ""
Log "===== 发布笔记 & 关注 Feed 流 ====="
$t3code = Api 'POST' "/user/code?phone=19999999999"
$t3 = (Api 'POST' "/user/login" (@{ phone = '19999999999'; code = "$($t3code.data)" } | ConvertTo-Json)).data
$t2code = Api 'POST' "/user/code?phone=18888888888"
$t2 = (Api 'POST' "/user/login" (@{ phone = '18888888888'; code = "$($t2code.data)" } | ConvertTo-Json)).data
# 1012 关注 1011，这样 1011 发笔记会进 1012 的收件箱
Api 'PUT' "/follow/1011/true" $null $t2 | Out-Null

$newBlog = Api 'POST' "/blog" (@{
    title = "联调测试笔记：运河边的宝藏咖啡店"
    content = "环境安静，手冲很稳，适合坐一下午。"
    images = "/imgs/blogs/7/14/4771fefb-1a87-4d6a-9d3f-000000000001.jpg"
    topicId = 4
    shopId = 4
    score = 46
    location = "拱宸桥/上塘"
} | ConvertTo-Json) $t3
Show "POST /blog 发布笔记" $newBlog (" :: id=" + $newBlog.data)
$newBlogId = $newBlog.data

$feed = Api 'GET' "/blog/of/follow?lastId=0&offset=0" $null $t2
Show "GET /blog/of/follow (关注流首页)" $feed (" :: " + (Count-Of ([pscustomobject]@{success=$true;data=$feed.data.list})) + " 条, minTime=" + $feed.data.minTime + ", offset=" + $feed.data.offset)
if ($feed.data.list) {
    $hit = $false
    foreach ($b in $feed.data.list) { if ($b.id -eq $newBlogId) { $hit = $true } }
    $hitMsg = if ($hit) { "OK  " } else { "WARN" }
    Log ("[$hitMsg] 新笔记是否出现在粉丝关注流: " + $hit)
}
$feed2 = Api 'GET' "/blog/of/follow?lastId=$($feed.data.minTime)&offset=$($feed.data.offset)" $null $t2
Show "GET /blog/of/follow (scroll 第二页)" $feed2 (" :: " + (Count-Of ([pscustomobject]@{success=$true;data=$feed2.data.list})) + " 条, minTime=" + $feed2.data.minTime + ", offset=" + $feed2.data.offset)

# 编辑 & 删除
$upd = Api 'PUT' "/blog" (@{ id = $newBlogId; title = "联调测试笔记（已编辑）"; content = "编辑后的内容"; images = "/imgs/blogs/7/14/4771fefb-1a87-4d6a-9d3f-000000000001.jpg"; topicId = 4; score = 48 } | ConvertTo-Json) $t3
Show "PUT /blog 编辑笔记" $upd ""
$detail2 = Api 'GET' "/blog/$newBlogId"
Show "GET /blog/{id} 校验编辑" $detail2 (" :: title=" + $detail2.data.title + " score=" + $detail2.data.score)
$other = Api 'DELETE' "/blog/$newBlogId" $null $t2
$otherOk = if ($other -and -not $other.success) { "OK  " } else { "FAIL" }
Log ("[$otherOk] 删除他人笔记应被拒绝 :: " + $other.errorMsg)
$del = Api 'DELETE' "/blog/$newBlogId" $null $t3
Show "DELETE /blog/{id} 本人删除" $del ""
$detail3 = Api 'GET' "/blog/$newBlogId"
$delOk = if ($detail3 -and -not $detail3.success) { "OK  " } else { "FAIL" }
Log ("[$delOk] 删除后详情应不可见 :: " + $detail3.errorMsg)

# ---------------------------------------------------------------- 商户 & 交易
Log ""
Log "===== 商户 / 交易闭环 ====="
$st = Api 'GET' "/shop-type/list";  Show "GET /shop-type/list" $st (" :: " + (Count-Of $st) + " 个分类")
$s1 = Api 'GET' "/shop/1";          Show "GET /shop/1" $s1 (" :: " + $s1.data.name)
$ss = Api 'GET' "/shop/search?keyword=%E7%81%AB%E9%94%85"; Show "GET /shop/search?keyword=火锅" $ss (" :: " + (Count-Of $ss) + " 家")
$so = Api 'GET' "/shop/of/type?typeId=1&current=1&x=120.149192&y=30.316078"; Show "GET /shop/of/type (GEO)" $so (" :: " + (Count-Of $so) + " 家")
$vch = Api 'GET' "/voucher/list/1"; Show "GET /voucher/list/1" $vch (" :: " + (Count-Of $vch) + " 张券")

$orders0 = Api 'GET' "/voucher-order/list" $null $token1
Show "GET /voucher-order/list (下单前)" $orders0 (" :: " + (Count-Of $orders0) + " 单")

$sk = Api 'POST' "/voucher-order/seckill/10" $null $token1
Show "POST /voucher-order/seckill/10" $sk (" :: orderId=" + $sk.data)
$skDup = Api 'POST' "/voucher-order/seckill/10" $null $token1
$dupOk = if ($skDup -and -not $skDup.success) { "OK  " } else { "FAIL" }
Log ("[$dupOk] 重复秒杀应被拒绝 :: " + $skDup.errorMsg)

Start-Sleep -Seconds 3
$orders1 = Api 'GET' "/voucher-order/list" $null $token1
Show "GET /voucher-order/list (下单后)" $orders1 (" :: " + (Count-Of $orders1) + " 单")
if ((Count-Of $orders1) -gt 0) {
    $o = $orders1.data[0]
    Log ("       最新订单: id=" + $o.id + " status=" + $o.status + " title=" + $o.title + " shop=" + $o.shopName + " payValue=" + $o.payValue)
    $od = Api 'GET' "/voucher-order/$($o.id)" $null $token1
    Show "GET /voucher-order/{id}" $od (" :: status=" + $od.data.status)
    if ($o.status -eq 1) {
        $pay = Api 'PUT' "/voucher-order/pay/$($o.id)" $null $token1
        Show "PUT /voucher-order/pay/{id}" $pay ""
        $od2 = Api 'GET' "/voucher-order/$($o.id)" $null $token1
        Show "  支付后状态" $od2 (" :: status=" + $od2.data.status + " (2=已支付)")
        $use = Api 'PUT' "/voucher-order/use/$($o.id)" $null $token1
        Show "PUT /voucher-order/use/{id} 核销" $use ""
        $od3 = Api 'GET' "/voucher-order/$($o.id)" $null $token1
        Show "  核销后状态" $od3 (" :: status=" + $od3.data.status + " (3=已核销)")
    }
    $otherOrder = Api 'GET' "/voucher-order/$($o.id)" $null $token2
    $oo = if ($otherOrder -and -not $otherOrder.success) { "OK  " } else { "FAIL" }
    Log ("[$oo] 查看他人订单应被拒绝 :: " + $otherOrder.errorMsg)
}

$byStatus = Api 'GET' "/voucher-order/list?status=2" $null $token1
Show "GET /voucher-order/list?status=2" $byStatus (" :: " + (Count-Of $byStatus) + " 单")

# ---------------------------------------------------------------- 登出
Log ""
Log "===== 登出 ====="
$lo = Api 'POST' "/user/logout" $null $token1
Show "POST /user/logout" $lo ""
$afterLogout = Api 'GET' "/user/me" $null $token1
$alOk = if ($afterLogout -and -not $afterLogout.success) { "OK  " } else { "FAIL" }
Log ("[$alOk] 登出后 token 应失效 :: " + $afterLogout.errorMsg)

Log ""
$failCount = ($out | Where-Object { $_ -match '^\[FAIL' }).Count
$warnCount = ($out | Where-Object { $_ -match '^\[WARN' }).Count
$okCount   = ($out | Where-Object { $_ -match '^\[OK' }).Count
Log "===== 汇总: OK=$okCount FAIL=$failCount WARN=$warnCount ====="

[System.IO.File]::WriteAllLines("D:\heima redis\实战篇\后端项目-初始状态\tools\api-smoke-test.result.txt", $out, [System.Text.UTF8Encoding]::new($false))
Write-Host "smoke test done: OK=$okCount FAIL=$failCount WARN=$warnCount"
