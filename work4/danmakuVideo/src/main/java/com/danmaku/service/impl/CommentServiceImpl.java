package com.danmaku.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danmaku.entity.Comment;
import com.danmaku.entity.Video;
import com.danmaku.mapper.CommentMapper;
import com.danmaku.mapper.VideoMapper;
import com.danmaku.service.CommentService;
import com.danmaku.util.JwtUtil;
import com.danmaku.vo.ResultVo;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Resource
    private VideoMapper videoMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> publishComment(String accessToken, String videoId, String content, String parentId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Comment comment = new Comment();
        comment.setUserId(String.valueOf(userId));
        comment.setVideoId(videoId);
        comment.setContent(content);
        comment.setParentId(parentId);
        save(comment);

        // 更新视频评论数
        Video video = videoMapper.selectById(videoId);
        video.setCommentCount(video.getCommentCount() + 1);
        videoMapper.updateById(video);

        // 如果是子评论，更新父评论子评论数
        if (!"0".equals(parentId)) {
            Comment parent = getById(parentId);
            if (parent != null) {
                parent.setChildCount(parent.getChildCount() + 1);
                updateById(parent);
            }
        }
        return ResultVo.success("评论成功");
    }

    @Override
    public ResultVo<?> getCommentList(String videoId, Integer pageNum, Integer pageSize) {
        Page<Comment> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getVideoId, videoId)
                .eq(Comment::getParentId, "0")
                .orderByDesc(Comment::getCreatedAt);
        IPage<Comment> iPage = page(page, wrapper);
        return ResultVo.success(iPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> deleteComment(String accessToken, String commentId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Comment comment = getById(commentId);
        if (comment == null) {
            return ResultVo.fail("评论不存在");
        }
        if (!comment.getUserId().equals(String.valueOf(userId))) {
            return ResultVo.fail("无权限删除");
        }

        boolean isParent = "0".equals(comment.getParentId());
        removeById(commentId);

        // 更新视频评论数
        Video video = videoMapper.selectById(comment.getVideoId());
        video.setCommentCount(Math.max(0, video.getCommentCount() - 1));
        videoMapper.updateById(video);

        // 如果是父评论，删除所有子评论
        if (isParent) {
            LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Comment::getParentId, commentId);
            long childCount = count(wrapper);
            remove(wrapper);
            video.setCommentCount((int) Math.max(0, video.getCommentCount() - childCount));
            videoMapper.updateById(video);
        } else {
            // 子评论，减少父评论子评论数
            Comment parent = getById(comment.getParentId());
            if (parent != null) {
                parent.setChildCount(Math.max(0, parent.getChildCount() - 1));
                updateById(parent);
            }
        }
        return ResultVo.success("删除成功");
    }
}