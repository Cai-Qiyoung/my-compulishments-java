package com.danmaku.util;

import cn.hutool.core.io.FileUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@Component
public class FileUploadUtil {

    private final String BASE_PATH = System.getProperty("user.dir") + "/upload/";
    private final String AVATAR_PATH = BASE_PATH + "avatar/";
    private final String VIDEO_PATH = BASE_PATH + "video/";
    private final String COVER_PATH = BASE_PATH + "cover/";
    @Value("${app.base-url:http://120.55.191.140:9090}")
    private String baseUrl;

    public String uploadAvatar(MultipartFile file) {
        return uploadFile(file, AVATAR_PATH, "avatar/");
    }

    public String uploadVideo(MultipartFile file) {
        return uploadFile(file, VIDEO_PATH, "video/");
    }

    public String uploadCover(MultipartFile file) {
        return uploadFile(file, COVER_PATH, "cover/");
    }

    private String uploadFile(MultipartFile file, String storePath, String visitSubPath) {
        try {
            FileUtil.mkdir(storePath);

            String originalFilename = file.getOriginalFilename();
            String suffix = FileUtil.extName(originalFilename);
            String newFileName = UUID.randomUUID() + "." + suffix;

            File dest = new File(storePath, newFileName);
            file.transferTo(dest);

            return normalizeBaseUrl() + "/upload/" + visitSubPath + newFileName;
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private String normalizeBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
