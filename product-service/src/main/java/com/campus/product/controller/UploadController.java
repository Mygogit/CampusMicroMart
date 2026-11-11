package com.campus.product.controller;

import com.campus.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Tag(name = "文件上传")
@RestController
@RequestMapping("/product")
public class UploadController {

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @Operation(summary = "上传商品图片")
    @PostMapping("/upload/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        // 大小限制：最多 5MB，防止资源滥用
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error("图片大小不能超过 5MB");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        // 只允许 PNG 和 JPG/JPEG 格式（扩展名白名单）
        if (!extension.equals(".png") && !extension.equals(".jpg") && !extension.equals(".jpeg")) {
            return Result.error("仅支持 PNG、JPG 格式的图片");
        }
        // 内容类型校验：拒绝伪装成图片的 HTML/JS 等文件
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equalsIgnoreCase("image/png")
                || contentType.equalsIgnoreCase("image/jpeg"))) {
            return Result.error("文件内容类型非法，仅支持 PNG、JPG");
        }
        String filename = UUID.randomUUID().toString() + extension;

        try {
            Path dir = Paths.get(uploadPath, "images").toAbsolutePath();
            Files.createDirectories(dir);
            File dest = dir.resolve(filename).toFile();
            file.transferTo(dest);
            String url = "/uploads/images/" + filename;
            log.info("图片上传成功: {}", url);
            return Result.success("上传成功", url);
        } catch (IOException e) {
            log.error("图片上传失败", e);
            return Result.error("图片上传失败: " + e.getMessage());
        }
    }
}
