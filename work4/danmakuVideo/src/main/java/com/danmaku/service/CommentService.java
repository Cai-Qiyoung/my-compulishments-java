package com.danmaku.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danmaku.entity.Comment;
import com.danmaku.vo.ResultVo;

public interface CommentService extends IService<Comment> {
    /**
     * 发布评论/回复
     * @param accessToken
     * @param videoId 视频ID
     * @param content 评论内容
     * @param parentId 父评论ID（0为根评论）
     * @return 发布结果
     */
    ResultVo<?> publishComment(String accessToken, String videoId, String content, String parentId);

    /**
     * 获取视频评论列表
     * @param videoId 视频ID
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 评论分页列表
     */
    ResultVo<?> getCommentList(String videoId, Integer pageNum, Integer pageSize);

    /**
     * 删除评论
     * @param accessToken
     * @param commentId 要删除的评论ID
     * @return 删除结果
     */
    ResultVo<?> deleteComment(String accessToken, String commentId);
}