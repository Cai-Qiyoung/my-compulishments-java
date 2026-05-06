package com.danmaku.service.impl;

import cn.hutool.core.util.IdUtil;
import com.danmaku.constant.RedisKeys;
import com.danmaku.entity.Comment;
import com.danmaku.entity.Like;
import com.danmaku.entity.Video;
import com.danmaku.mapper.CommentMapper;
import com.danmaku.mapper.LikeMapper;
import com.danmaku.mapper.VideoMapper;
import com.danmaku.service.LikeService;
import com.danmaku.util.JwtUtil;
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
public class LikeServiceImpl implements LikeService {
    private static final long LIKE_LIST_CACHE_TTL_MINUTES = 5L;
    private static final long LIKE_LOCK_TTL_SECONDS = 5L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Resource
    private VideoMapper videoMapper;
    @Resource
    private CommentMapper commentMapper;
    @Resource
    private LikeMapper likeMapper;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> likeVideo(String accessToken, String videoId) {
        Long userId = jwtUtil.getUserIdFromToken(accessToken);
        String lockKey = RedisKeys.likeVideoLock(String.valueOf(userId), videoId);
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(lockKey, lockValue, LIKE_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            return ResultVo.fail("操作过于频繁，请稍后重试");
        }

        try {
            Video video = videoMapper.selectById(videoId);
            if (video == null) {
                return ResultVo.fail("视频不存在");
            }

            Like like = likeMapper.selectVideoLike(String.valueOf(userId), videoId, 1);

            ResultVo<?> result;
            if (like == null) {
                Like newLike = new Like();
                newLike.setId(IdUtil.getSnowflakeNextIdStr());
                newLike.setUserId(String.valueOf(userId));
                newLike.setVideoId(videoId);
                newLike.setType(1);
                likeMapper.insert(newLike);
                videoMapper.incrementLikeCount(videoId, 1);
                redisUtil.opsForZSet().incrementScore(RedisKeys.VIDEO_POPULAR_ZSET, videoId, 2D);
                result = ResultVo.success("点赞成功");
            } else {
                likeMapper.deleteById(like.getId());
                videoMapper.adjustLikeCountSafely(videoId, -1);
                redisUtil.opsForZSet().incrementScore(RedisKeys.VIDEO_POPULAR_ZSET, videoId, -2D);
                result = ResultVo.success("取消点赞成功");
            }

            invalidateVideoLikeCaches(videoId, video.getUserId(), String.valueOf(userId));
            return result;
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> likeComment(String accessToken, String commentId) {
        Long userId = jwtUtil.getUserIdFromToken(accessToken);
        String lockKey = RedisKeys.likeCommentLock(String.valueOf(userId), commentId);
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(lockKey, lockValue, LIKE_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            return ResultVo.fail("操作过于频繁，请稍后重试");
        }

        try {
            Comment comment = commentMapper.selectById(commentId);
            if (comment == null) {
                return ResultVo.fail("评论不存在");
            }

            Like like = likeMapper.selectCommentLike(String.valueOf(userId), commentId, 2);

            ResultVo<?> result;
            if (like == null) {
                Like newLike = new Like();
                newLike.setId(IdUtil.getSnowflakeNextIdStr());
                newLike.setUserId(String.valueOf(userId));
                newLike.setCommentId(commentId);
                newLike.setType(2);
                likeMapper.insert(newLike);
                commentMapper.incrementLikeCount(commentId, 1);
                result = ResultVo.success("点赞成功");
            } else {
                likeMapper.deleteById(like.getId());
                commentMapper.adjustLikeCountSafely(commentId, -1);
                result = ResultVo.success("取消点赞成功");
            }

            invalidateCommentCaches(comment.getVideoId(), String.valueOf(userId));
            return result;
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    @Override
    public ResultVo<?> likeList(String accessToken, String userId, Integer pageNum, Integer pageSize) {
        try {
            String targetUserId = resolveTargetUserId(accessToken, userId);
            if (targetUserId == null) {
                return ResultVo.fail("未登录且未指定用户ID");
            }

            int safePageNum = normalizePageNum(pageNum);
            int safePageSize = normalizePageSize(pageSize);
            String cacheKey = RedisKeys.likeList(targetUserId, safePageNum, safePageSize);
            Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
            if (cached != null) {
                return ResultVo.success(cached);
            }

            long total = likeMapper.countByUserId(targetUserId);
            List<Like> likeList = total == 0
                    ? Collections.emptyList()
                    : likeMapper.selectByUserPageWithVideo(targetUserId, calcOffset(safePageNum, safePageSize), safePageSize);

            Map<String, Object> data = new HashMap<>();
            data.put("items", buildLikeItems(likeList));
            data.put("total", total);
            data.put("current", safePageNum);
            data.put("size", safePageSize);
            data.put("pages", calcPages(total, safePageSize));

            redisUtil.setCacheObjectSafely(cacheKey, data, LIKE_LIST_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return ResultVo.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("获取喜欢列表失败：" + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildLikeItems(List<Like> likeList) {
        if (likeList == null || likeList.isEmpty()) {
            return Collections.emptyList();
        }

        return likeList.stream()
                .map(like -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", like.getId());
                    item.put("user_id", like.getUserId());
                    item.put("video_id", like.getVideoId());
                    item.put("comment_id", like.getCommentId());
                    item.put("type", like.getType());
                    if (like.getVideo() != null) {
                        item.put("video", toVideoItem(like.getVideo()));
                    }
                    return item;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> toVideoItem(Video video) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", video.getId());
        item.put("user_id", video.getUserId());
        item.put("video_url", video.getVideoUrl());
        item.put("cover_url", video.getCoverUrl());
        item.put("title", video.getTitle());
        item.put("description", video.getDescription());
        item.put("visit_count", defaultInteger(video.getVisitCount()));
        item.put("like_count", defaultInteger(video.getLikeCount()));
        item.put("comment_count", defaultInteger(video.getCommentCount()));
        item.put("created_at", formatDateTime(video.getCreatedAt()));
        item.put("updated_at", formatDateTime(video.getUpdatedAt()));
        item.put("deleted_at", formatDateTime(video.getDeletedAt()));
        return item;
    }

    private void invalidateVideoLikeCaches(String videoId, String ownerUserId, String likeUserId) {
        redisUtil.deleteObject(RedisKeys.videoDetail(videoId));
        redisUtil.deleteByPrefix(RedisKeys.popularVideoPrefix());
        if (ownerUserId != null) {
            redisUtil.deleteByPrefix(RedisKeys.videoListPrefix(ownerUserId));
        }
        if (likeUserId != null) {
            redisUtil.deleteByPrefix(RedisKeys.likeListPrefix(likeUserId));
        }
    }

    private void invalidateCommentCaches(String videoId, String likeUserId) {
        redisUtil.deleteByPrefix(RedisKeys.commentListPrefix(videoId));
        if (likeUserId != null) {
            redisUtil.deleteByPrefix(RedisKeys.likeListPrefix(likeUserId));
        }
    }

    private String resolveTargetUserId(String accessToken, String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        Long currentUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return currentUserId == null ? null : currentUserId.toString();
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
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
