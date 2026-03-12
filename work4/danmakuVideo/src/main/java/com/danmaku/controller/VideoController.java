package com.danmaku.controller;

import com.danmaku.service.VideoService;
import com.danmaku.vo.ResultVo;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/video")
public class VideoController {
    @Resource
    private VideoService videoService;


    /**
     * 视频投稿
     */
    @PostMapping("/publish")
    public ResultVo<?> publishVideo(
            @RequestHeader("Access-Token") String accessToken,
            @RequestParam MultipartFile videoFile,
            @RequestParam MultipartFile coverFile,
            @RequestParam String title,
            @RequestParam(required = false) String description) {
        return videoService.publishVideo(accessToken, videoFile, coverFile, title, description);
    }

    /**
     * 获取用户发布列表
     */
    @GetMapping("/list")
    public ResultVo<?> getVideoList(@RequestHeader(required = false , name = "Access-Token") String accessToken,
                                    @RequestParam(required = false) String user_id,
                                    @RequestParam(defaultValue = "1") Integer page_num,
                                    @RequestParam(defaultValue = "10") Integer page_size) {
        return videoService.getVideoList(accessToken,user_id, page_num, page_size);
    }

    /**
     * 热门排行榜
     */
    @GetMapping("/popular")
    public ResultVo<?> getPopularVideo(@RequestParam(defaultValue = "1") Integer page_num,
                                       @RequestParam(defaultValue = "10") Integer page_size) {
        return videoService.getPopularVideo(page_num - 1, page_size);
    }

    /**
     * 视频搜索
     */
    @PostMapping("/search")
    public ResultVo<?> searchVideo(
            @RequestParam String keywords,
            @RequestParam(defaultValue = "1") Integer page_num,
            @RequestParam(defaultValue = "10") Integer page_size
    ) {
        return videoService.searchVideo(keywords, page_num, page_size);
    }

    /**
     * 获取视频详情
     */
    @GetMapping("/detail")
    public ResultVo<?> getVideoDetail(@RequestParam String video_id) {
        return ResultVo.success(videoService.getById(video_id));
    }
}