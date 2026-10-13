package com.cityhub.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.cityhub.config.CityHubProperties;
import com.cityhub.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传。文件直接写入 nginx 静态资源目录，由 nginx 提供访问。
 *
 * @author CityHub
 */
@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    /** 允许的图片后缀 */
    private static final Set<String> ALLOWED_SUFFIX = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    @Resource
    private CityHubProperties props;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return Result.fail("请选择要上传的图片");
        }
        String originalFilename = image.getOriginalFilename();
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(suffix)) {
            return Result.fail("无法识别图片格式");
        }
        suffix = suffix.toLowerCase(Locale.ROOT);
        if (!ALLOWED_SUFFIX.contains(suffix)) {
            return Result.fail("仅支持 " + String.join("/", ALLOWED_SUFFIX) + " 格式的图片");
        }
        try {
            String fileName = createNewFileName(suffix);
            File target = new File(props.getImageUploadDir(), fileName);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return Result.fail("创建上传目录失败");
            }
            image.transferTo(target);
            log.debug("文件上传成功，{}", fileName);
            // 返回带前导 / 的相对路径，前端拼接后即为 nginx 可访问地址
            return Result.ok(fileName.replace('\\', '/'));
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        if (StrUtil.isBlank(filename)) {
            return Result.fail("错误的文件名称");
        }
        // 只允许删除 imgs 目录下的文件，避免路径穿越
        String relative = filename.replace('\\', '/');
        if (relative.contains("..")) {
            return Result.fail("错误的文件名称");
        }
        File file = new File(props.getImageUploadDir(), relative);
        if (!file.exists() || file.isDirectory()) {
            return Result.fail("文件不存在");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    /**
     * 生成 /blogs/{d1}/{d2}/{uuid}.{suffix} 形式的相对路径。
     */
    private String createNewFileName(String suffix) {
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        File dir = new File(props.getImageUploadDir(), StrUtil.format("blogs/{}/{}", d1, d2));
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("创建上传目录失败: " + dir.getAbsolutePath());
        }
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
