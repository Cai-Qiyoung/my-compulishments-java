package com.danmaku.util;

import cn.hutool.core.io.FileUtil;
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
    private final String VISIT_PREFIX = "http://localhost:10001/upload/";

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

            return VISIT_PREFIX + visitSubPath + newFileName;
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }
}