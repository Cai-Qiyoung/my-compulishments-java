package com.danmaku.controller;

import com.danmaku.service.LikeService;
import com.danmaku.vo.ResultVo;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/like")
public class LikeController {
    @Resource
    private LikeService likeService;

    /**
     * 点赞/取消点赞视频
     */
    @PostMapping("/video")
    public ResultVo<?> likeVideo(@RequestParam String video_id,
                                 @RequestHeader("Access-Token") String accessToken) {
        return likeService.likeVideo(accessToken, video_id);
    }

    /**
     * 点赞/取消点赞评论
     */
    @PostMapping("/comment")
    public ResultVo<?> likeComment(@RequestParam String comment_id,
                                   @RequestHeader("Access-Token") String accessToken) {
        return likeService.likeComment(accessToken, comment_id);
    }

    /**
     * 点赞列表
     */
    @GetMapping("/list") ResultVo<?> likeList(@RequestHeader(required = false , name = "Access-Token") String accessToken,
                                        @RequestParam(required = false)  String user_id,
                                        @RequestParam(defaultValue = "1") Integer page_num,
                                        @RequestParam(defaultValue = "10") Integer page_size){
        return likeService.likeList(accessToken,user_id,page_num,page_size);
    }
}