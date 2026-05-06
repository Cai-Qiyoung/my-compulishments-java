package com.danmaku.service.impl;

import cn.hutool.core.util.IdUtil;
import com.danmaku.constant.RedisKeys;
import com.danmaku.entity.Comment;
import com.danmaku.entity.Video;
import com.danmaku.mapper.CommentMapper;
import com.danmaku.mapper.VideoMapper;
import com.danmaku.service.CommentService;
import com.danmaku.util.RedisUtil;
import com.danmaku.vo.ResultVo;
import jakarta.annotation.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {
    private static final long COMMENT_LIST_CACHE_TTL_MINUTES = 3L;
    private static final long COMMENT_DELETE_LOCK_TTL_SECONDS = 5L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Resource
    private VideoMapper videoMapper;
    @Resource
    private CommentMapper commentMapper;
    @Resource
    private RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> publishComment(String accessToken, String videoId, String content, String parentId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            return ResultVo.fail("视频不存在");
        }

        String normalizedParentId = (parentId == null || parentId.isBlank()) ? "0" : parentId;
        if (!"0".equals(normalizedParentId) && commentMapper.selectById(normalizedParentId) == null) {
            return ResultVo.fail("父评论不存在");
        }

        Comment comment = new Comment();
        comment.setId(IdUtil.getSnowflakeNextIdStr());
        comment.setUserId(String.valueOf(userId));
        comment.setVideoId(videoId);
        comment.setContent(content);
        comment.setParentId(normalizedParentId);
        comment.setLikeCount(0);
        comment.setChildCount(0);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        videoMapper.incrementCommentCount(videoId, 1);
        if (!"0".equals(normalizedParentId)) {
            commentMapper.incrementChildCount(normalizedParentId, 1);
        }

        invalidateCommentCaches(videoId, video.getUserId());
        return ResultVo.success("评论成功");
    }

    @Override
    public ResultVo<?> getCommentList(String videoId, Integer pageNum, Integer pageSize) {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        String cacheKey = RedisKeys.commentList(videoId, safePageNum, safePageSize);
        Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
        if (cached != null) {
            return ResultVo.success(cached);
        }

        long total = commentMapper.countRootComments(videoId);
        List<Comment> records = total == 0
                ? Collections.emptyList()
                : commentMapper.selectRootCommentsPage(videoId, calcOffset(safePageNum, safePageSize), safePageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("records", buildCommentItems(records));
        data.put("total", total);
        data.put("size", safePageSize);
        data.put("current", safePageNum);
        data.put("pages", calcPages(total, safePageSize));
        redisUtil.setCacheObjectSafely(cacheKey, data, COMMENT_LIST_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> deleteComment(String accessToken, String commentId) {
        String lockKey = RedisKeys.commentDeleteLock(commentId);
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(lockKey, lockValue, COMMENT_DELETE_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            return ResultVo.fail("操作过于频繁，请稍后重试");
        }

        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Comment comment = commentMapper.selectById(commentId);
            if (comment == null) {
                return ResultVo.fail("评论不存在");
            }
            if (!comment.getUserId().equals(String.valueOf(userId))) {
                return ResultVo.fail("无权限删除");
            }

            Video video = videoMapper.selectById(comment.getVideoId());
            String ownerUserId = video == null ? null : video.getUserId();

            boolean isParent = "0".equals(comment.getParentId());
            commentMapper.deleteById(commentId);

            if (isParent) {
                long childCount = commentMapper.countByParentId(commentId);
                if (childCount > 0) {
                    commentMapper.deleteByParentId(commentId);
                }
                videoMapper.adjustCommentCountSafely(comment.getVideoId(), -(childCount + 1));
            } else {
                commentMapper.adjustChildCountSafely(comment.getParentId(), -1);
                videoMapper.adjustCommentCountSafely(comment.getVideoId(), -1);
            }

            invalidateCommentCaches(comment.getVideoId(), ownerUserId);
            return ResultVo.success("删除成功");
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    private void invalidateCommentCaches(String videoId, String videoOwnerUserId) {
        redisUtil.deleteByPrefix(RedisKeys.commentListPrefix(videoId));
        redisUtil.deleteObject(RedisKeys.videoDetail(videoId));
        redisUtil.deleteByPrefix(RedisKeys.popularVideoPrefix());
        if (videoOwnerUserId != null) {
            redisUtil.deleteByPrefix(RedisKeys.videoListPrefix(videoOwnerUserId));
        }
    }

    private List<Map<String, Object>> buildCommentItems(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }

        return comments.stream()
                .map(comment -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", comment.getId());
                    item.put("user_id", comment.getUserId());
                    item.put("video_id", comment.getVideoId());
                    item.put("parent_id", comment.getParentId());
                    item.put("like_count", defaultInteger(comment.getLikeCount()));
                    item.put("child_count", defaultInteger(comment.getChildCount()));
                    item.put("content", comment.getContent());
                    item.put("created_at", formatDateTime(comment.getCreatedAt()));
                    item.put("updated_at", formatDateTime(comment.getUpdatedAt()));
                    item.put("deleted_at", formatDateTime(comment.getDeletedAt()));
                    return item;
                })
                .collect(Collectors.toList());
    }

    private int calcOffset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

    private long calcPages(long total, int pageSize) {
        if (pageSize <= 0) {
            return 0L;
        }
        return (total + pageSize - 1) / pageSize;
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME_FORMATTER.format(dateTime);
    }
}
