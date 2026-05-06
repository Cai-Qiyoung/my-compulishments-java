package com.danmaku.controller;

import com.danmaku.service.CommentService;
import com.danmaku.vo.ResultVo;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/comment")
public class CommentController {
    @Resource
    private CommentService commentService;

    /**
     * 发布评论
     */
    @PostMapping("/publish")
    public ResultVo<?> publishComment(@RequestHeader("Access-Token") String access_token,
                                      @RequestParam String video_id,
                                      @RequestParam String content,
                                      @RequestParam(defaultValue = "0") String parent_id) {
        return commentService.publishComment(access_token, video_id, content, parent_id);
    }

    /**
     * 获取视频评论列表
     */
    @GetMapping("/list")
    public ResultVo<?> getCommentList(@RequestParam String video_id,
                                      @RequestParam(defaultValue = "1") Integer page_num,
                                      @RequestParam(defaultValue = "10") Integer page_size) {
        return commentService.getCommentList(video_id, page_num, page_size);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/delete")
    public ResultVo<?> deleteComment(@RequestHeader("Access-Token") String access_token,
                                     @RequestParam String comment_id) {
        return commentService.deleteComment(access_token, comment_id);
    }
}