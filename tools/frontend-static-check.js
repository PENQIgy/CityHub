/*
 * CityHub 前端静态自检
 *  1) 抽取每个页面内的 <script> 内联代码，用 vm.Script 做语法检查（不执行）
 *  2) 收集 util.xxx / chAuth.xxx / chToast 的调用点，与 common.js 中真正定义的方法比对
 *  3) 检查是否引用了不存在的本地资源文件
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = 'D:\\heima redis\\实战篇\\后端项目-初始状态\\front\\html\\cityhub';
const problems = [];
const info = [];

/* ---------------------------------------------------------- 1. 解析 common.js 暴露的 API */
const commonSrc = fs.readFileSync(path.join(ROOT, 'js', 'common.js'), 'utf8');

// util 对象字面量里的方法名
const utilKeys = new Set();
const utilBlock = commonSrc.match(/const util = \{([\s\S]*?)\n\};/);
if (utilBlock) {
    const re = /^\s{4}([A-Za-z_$][\w$]*)\s*\(/gm;
    let m;
    while ((m = re.exec(utilBlock[1])) !== null) utilKeys.add(m[1]);
    // 也匹配 `name: value` 形式
    const re2 = /^\s{4}([A-Za-z_$][\w$]*)\s*:/gm;
    while ((m = re2.exec(utilBlock[1])) !== null) utilKeys.add(m[1]);
}
['commonURL', 'DEFAULT_AVATAR'].forEach(k => utilKeys.add(k));

// chAuth 的方法
const chAuthKeys = new Set();
const authBlock = commonSrc.match(/const chAuth = \{([\s\S]*?)\n\};/);
if (authBlock) {
    const re = /^\s{4}([A-Za-z_$][\w$]*)\s*\(/gm;
    let m;
    while ((m = re.exec(authBlock[1])) !== null) chAuthKeys.add(m[1]);
}
info.push('util 暴露的方法: ' + Array.from(utilKeys).sort().join(', '));
info.push('chAuth 暴露的方法: ' + Array.from(chAuthKeys).sort().join(', '));
info.push('');

/* ---------------------------------------------------------- 2. 遍历页面 */
const htmlFiles = fs.readdirSync(ROOT).filter(f => f.endsWith('.html')).sort();

function extractInlineScripts(html) {
    const out = [];
    const re = /<script(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;
    let m;
    while ((m = re.exec(html)) !== null) out.push(m[1]);
    return out;
}

for (const file of htmlFiles) {
    const full = path.join(ROOT, file);
    const html = fs.readFileSync(full, 'utf8');
    const scripts = extractInlineScripts(html);

    // --- 语法检查
    let syntaxOk = true;
    scripts.forEach((code, i) => {
        try {
            new vm.Script(code, { filename: `${file}#script${i}` });
        } catch (e) {
            syntaxOk = false;
            problems.push(`[语法错误] ${file} 第 ${i + 1} 段内联脚本: ${e.message}`);
        }
    });

    // --- util.xxx / chAuth.xxx 引用检查
    const badUtil = new Set();
    const badAuth = new Set();
    let m;
    const reU = /\butil\.([A-Za-z_$][\w$]*)/g;
    while ((m = reU.exec(html)) !== null) {
        if (!utilKeys.has(m[1])) badUtil.add(m[1]);
    }
    const reA = /\bchAuth\.([A-Za-z_$][\w$]*)/g;
    while ((m = reA.exec(html)) !== null) {
        if (!chAuthKeys.has(m[1])) badAuth.add(m[1]);
    }
    if (badUtil.size) problems.push(`[util 方法不存在] ${file}: ${Array.from(badUtil).join(', ')}`);
    if (badAuth.size) problems.push(`[chAuth 方法不存在] ${file}: ${Array.from(badAuth).join(', ')}`);

    // --- 本地资源引用检查
    const reSrc = /(?:src|href)="(\.\/[^"]+)"/g;
    while ((m = reSrc.exec(html)) !== null) {
        const rel = m[1];
        const target = path.join(ROOT, rel.replace(/^\.\//, ''));
        if (!fs.existsSync(target)) {
            problems.push(`[资源不存在] ${file}: ${rel}`);
        }
    }

    // --- 关键约定检查
    const checks = [];
    checks.push(html.includes('cityhub.css') ? 'css✓' : 'css✗');
    checks.push(html.includes('js/common.js') ? 'common✓' : 'common✗');
    checks.push(html.includes('js/footer.js') ? 'footer✓' : 'footer✗');
    checks.push(/id="app"/.test(html) ? 'app✓' : 'app✗');
    checks.push(syntaxOk ? 'syntax✓' : 'syntax✗');
    info.push(`${file.padEnd(20)} ${checks.join(' ')}  scripts=${scripts.length}`);
}

/* ---------------------------------------------------------- 3. 单独检查共享 js */
for (const js of ['js/common.js', 'js/footer.js']) {
    const full = path.join(ROOT, js);
    const src = fs.readFileSync(full, 'utf8');
    try {
        new vm.Script(src, { filename: js });
        info.push(`${js.padEnd(20)} syntax✓`);
    } catch (e) {
        problems.push(`[语法错误] ${js}: ${e.message}`);
    }
}

/* ---------------------------------------------------------- 输出 */
console.log('================ 页面自检 ================');
info.forEach(l => console.log(l));
console.log('');
if (problems.length === 0) {
    console.log('================ 未发现问题 ================');
} else {
    console.log(`================ 发现 ${problems.length} 个问题 ================`);
    problems.forEach(p => console.log('  ' + p));
}
process.exitCode = problems.length ? 1 : 0;
