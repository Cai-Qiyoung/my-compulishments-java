package com.danmaku.service.impl;

import cn.hutool.core.util.IdUtil;
import com.danmaku.constant.RedisKeys;
import com.danmaku.constant.VideoAuditStatus;
import com.danmaku.entity.User;
import com.danmaku.entity.Video;
import com.danmaku.mapper.LikeMapper;
import com.danmaku.mapper.UserMapper;
import com.danmaku.mapper.VideoMapper;
import com.danmaku.service.VideoService;
import com.danmaku.util.FileUploadUtil;
import com.danmaku.util.RedisUtil;
import com.danmaku.vo.ResultVo;
import com.danmaku.vo.VideoAuditVO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class VideoServiceImpl implements VideoService {
    private static final long VIDEO_DETAIL_CACHE_TTL_MINUTES = 5L;
    private static final long VIDEO_LIST_CACHE_TTL_MINUTES = 5L;
    private static final long POPULAR_VIDEO_CACHE_TTL_MINUTES = 2L;
    private static final long VIDEO_AUDIT_CACHE_TTL_MINUTES = 2L;
    private static final long VIDEO_AUDIT_LOCK_TTL_SECONDS = 5L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Resource
    private VideoMapper videoMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private LikeMapper likeMapper;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private FileUploadUtil fileUploadUtil;

    @Override
    public ResultVo<?> publishVideo(String accessToken, MultipartFile videoFile, MultipartFile coverFile, String title, String description) {
        try {
            Long userId = currentUserId();
            String videoUrl = fileUploadUtil.uploadVideo(videoFile);
            String coverUrl = fileUploadUtil.uploadCover(coverFile);

            Video video = new Video();
            video.setId(IdUtil.getSnowflakeNextIdStr());
            video.setUserId(String.valueOf(userId));
            video.setTitle(title);
            video.setDescription(description);
            video.setVideoUrl(videoUrl);
            video.setCoverUrl(coverUrl);
            video.setVisitCount(0);
            video.setLikeCount(0);
            video.setCommentCount(0);
            video.setAuditStatus(VideoAuditStatus.PENDING);
            video.setCreatedAt(LocalDateTime.now());
            video.setUpdatedAt(LocalDateTime.now());
            videoMapper.insert(video);

            invalidateVideoCaches(video.getId(), String.valueOf(userId));
            return ResultVo.success("发布成功！");
        } catch (Exception e) {
            throw new RuntimeException("投稿失败", e);
        }
    }

    @Override
    public ResultVo<Map<String, Object>> getVideoList(String accessToken, String userId, Integer pageNum, Integer pageSize) {
        try {
            String targetUserId = resolveTargetUserId(accessToken, userId);
            if (targetUserId == null) {
                return ResultVo.fail("未登录且未指定用户ID");
            }

            Long currentUserId = authenticatedUserIdOrNull();
            boolean includePending = currentUserId != null && targetUserId.equals(String.valueOf(currentUserId));
            int safePageNum = normalizePageNum(pageNum);
            int safePageSize = normalizePageSize(pageSize);
            String cacheKey = RedisKeys.videoList(targetUserId, safePageNum, safePageSize);
            Map<String, Object> cached = includePending ? null : redisUtil.getCacheObjectSafely(cacheKey);
            if (cached != null) {
                return ResultVo.success(cached);
            }

            long total = videoMapper.countByUserId(targetUserId, includePending);
            List<Video> videos = total == 0
                    ? Collections.emptyList()
                    : videoMapper.selectByUserIdPage(targetUserId, includePending, calcOffset(safePageNum, safePageSize), safePageSize);

            Map<String, Object> data = buildVideoPageData(videos, total, safePageNum, safePageSize);
            if (!includePending) {
                redisUtil.setCacheObjectSafely(cacheKey, data, VIDEO_LIST_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            return ResultVo.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("获取发布列表失败：" + e.getMessage());
        }
    }

    @Override
    public ResultVo<Map<String, Object>> getPopularVideo(Integer pageNum, Integer pageSize) {
        try {
            int safePageNum = normalizePageNum(pageNum);
            int safePageSize = normalizePageSize(pageSize);
            String cacheKey = RedisKeys.popularVideo(safePageNum, safePageSize);
            Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
            if (cached != null) {
                return ResultVo.success(cached);
            }

            long start = (long) (safePageNum - 1) * safePageSize;
            long end = start + safePageSize - 1;
            Set<ZSetOperations.TypedTuple<Object>> tuples =
                    redisUtil.opsForZSet().reverseRangeWithScores(RedisKeys.VIDEO_POPULAR_ZSET, start, end);

            if (tuples == null || tuples.isEmpty()) {
                Map<String, Object> emptyMap = new HashMap<>();
                emptyMap.put("items", Collections.emptyList());
                emptyMap.put("total", 0L);
                emptyMap.put("current", safePageNum);
                emptyMap.put("size", safePageSize);
                redisUtil.setCacheObjectSafely(cacheKey, emptyMap, POPULAR_VIDEO_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                return ResultVo.success(emptyMap);
            }

            List<String> videoIds = new ArrayList<>();
            Map<String, Double> scoreMap = new HashMap<>();
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                if (tuple == null || tuple.getValue() == null) {
                    continue;
                }
                String currentVideoId = tuple.getValue().toString();
                videoIds.add(currentVideoId);
                scoreMap.put(currentVideoId, tuple.getScore() == null ? 0D : tuple.getScore());
            }

            List<Video> videos = videoIds.isEmpty() ? Collections.emptyList() : videoMapper.selectByIds(videoIds);
            Map<String, Video> videoMap = videos.stream()
                    .collect(Collectors.toMap(Video::getId, video -> video, (left, right) -> left, LinkedHashMap::new));

            List<Video> orderedVideos = videoIds.stream()
                    .map(videoMap::get)
                    .filter(Objects::nonNull)
                    .sorted((left, right) -> Double.compare(
                            scoreMap.getOrDefault(right.getId(), 0D),
                            scoreMap.getOrDefault(left.getId(), 0D)
                    ))
                    .collect(Collectors.toList());

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("items", buildVideoItems(orderedVideos));
            resultMap.put("total", redisUtil.opsForZSet().zCard(RedisKeys.VIDEO_POPULAR_ZSET));
            resultMap.put("current", safePageNum);
            resultMap.put("size", safePageSize);

            redisUtil.setCacheObjectSafely(cacheKey, resultMap, POPULAR_VIDEO_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return ResultVo.success(resultMap);
        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("获取热门视频失败：" + e.getMessage());
        }
    }

    @Override
    public ResultVo<?> searchVideo(String keywords, Integer pageNum, Integer pageSize) {
        String normalizedKeywords = keywords == null ? "" : keywords.trim();
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        List<String> matchedUserIds = normalizedKeywords.isEmpty()
                ? Collections.emptyList()
                : userMapper.selectIdsByUsernameLike(normalizedKeywords);

        long total = videoMapper.countSearchVideos(normalizedKeywords, matchedUserIds, VideoAuditStatus.APPROVED);
        List<Video> videos = total == 0
                ? Collections.emptyList()
                : videoMapper.searchVideos(
                        normalizedKeywords,
                        matchedUserIds,
                        VideoAuditStatus.APPROVED,
                        calcOffset(safePageNum, safePageSize),
                        safePageSize
                );

        Map<String, Object> data = buildVideoPageData(videos, total, safePageNum, safePageSize);
        return ResultVo.success(data);
    }

    @Override
    public ResultVo<Map<String, Object>> getVideoDetail(String videoId, String accessToken) {
        try {
            if (videoId == null || videoId.isBlank()) {
                return ResultVo.fail("视频ID不能为空");
            }

            String cacheKey = RedisKeys.videoDetail(videoId);
            Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
            Map<String, Object> detailData;
            if (cached != null) {
                detailData = new HashMap<>(cached);
            } else {
                Video video = videoMapper.selectById(videoId);
                if (video == null) {
                    return ResultVo.fail("视频不存在");
                }
                detailData = buildVideoDetail(video);
            }

            incrementVisitCount(videoId);
            Integer visitCount = toInteger(detailData.get("visit_count"));
            detailData.put("visit_count", visitCount + 1);

            Long currentUserId = authenticatedUserIdOrNull();
            if (currentUserId != null) {
                detailData.put("is_liked", likeMapper.countVideoLikeByUser(String.valueOf(currentUserId), videoId, 1) > 0);
            }

            redisUtil.setCacheObjectSafely(cacheKey, new HashMap<>(detailData), VIDEO_DETAIL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return ResultVo.success(detailData);
        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("获取视频详情失败：" + e.getMessage());
        }
    }

    @Override
    public ResultVo<?> getPendingAuditVideos(Integer pageNum, Integer pageSize) {
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        String cacheKey = RedisKeys.videoAuditPending(safePageNum, safePageSize);
        Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
        if (cached != null) {
            return ResultVo.success(cached);
        }

        long total = videoMapper.countPendingAuditVideos();
        List<Video> videos = total == 0
                ? Collections.emptyList()
                : videoMapper.selectPendingAuditVideos(calcOffset(safePageNum, safePageSize), safePageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("items", buildAuditItems(videos));
        data.put("total", total);
        data.put("current", safePageNum);
        data.put("size", safePageSize);
        data.put("pages", calcPages(total, safePageSize));
        redisUtil.setCacheObjectSafely(cacheKey, data, VIDEO_AUDIT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> reviewVideo(String accessToken, String videoId, String auditStatus, String auditReason) {
        if (!VideoAuditStatus.APPROVED.equals(auditStatus) && !VideoAuditStatus.REJECTED.equals(auditStatus)) {
            return ResultVo.fail("审核状态不合法");
        }
        String reviewerUserId = String.valueOf(currentUserId());
        String lockKey = RedisKeys.videoAuditLock(videoId);
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(lockKey, lockValue, VIDEO_AUDIT_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            return ResultVo.fail("审核操作过于频繁，请稍后重试");
        }

        try {
            Video pendingVideo = videoMapper.selectAdminById(videoId);
            if (pendingVideo == null || !VideoAuditStatus.PENDING.equals(pendingVideo.getAuditStatus())) {
                return ResultVo.fail("待审核视频不存在");
            }

            int updated = videoMapper.reviewVideo(videoId, auditStatus, auditReason, reviewerUserId);
            if (updated <= 0) {
                return ResultVo.fail("视频已被审核，请刷新后重试");
            }
            if (VideoAuditStatus.APPROVED.equals(auditStatus)) {
                redisUtil.opsForZSet().add(RedisKeys.VIDEO_POPULAR_ZSET, videoId, (double) defaultInteger(pendingVideo.getVisitCount()));
            } else {
                redisUtil.opsForZSet().remove(RedisKeys.VIDEO_POPULAR_ZSET, videoId);
            }
            invalidateVideoCaches(videoId, pendingVideo.getUserId());
            redisUtil.deleteByPrefix(RedisKeys.videoAuditPendingPrefix());
            return ResultVo.success("审核完成");
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    public void incrementVisitCount(String videoId) {
        int updated = videoMapper.incrementVisitCount(videoId, 1);
        if (updated > 0) {
            redisUtil.opsForZSet().incrementScore(RedisKeys.VIDEO_POPULAR_ZSET, videoId, 1D);
        }
    }

    public void invalidateVideoCaches(String videoId, String userId) {
        redisUtil.deleteObject(RedisKeys.videoDetail(videoId));
        redisUtil.deleteByPrefix(RedisKeys.popularVideoPrefix());
        if (userId != null && !userId.isBlank()) {
            redisUtil.deleteByPrefix(RedisKeys.videoListPrefix(userId));
        }
    }

    private Map<String, Object> buildVideoDetail(Video video) {
        User publisher = userMapper.selectById(video.getUserId());
        Map<String, Object> publisherInfo = new HashMap<>();
        if (publisher != null) {
            publisherInfo.put("id", publisher.getId());
            publisherInfo.put("username", publisher.getUsername());
            publisherInfo.put("avatar_url", publisher.getAvatarUrl());
        }

        Map<String, Object> videoDetail = new HashMap<>();
        videoDetail.put("id", video.getId());
        videoDetail.put("title", video.getTitle());
        videoDetail.put("description", video.getDescription());
        videoDetail.put("video_url", video.getVideoUrl());
        videoDetail.put("cover_url", video.getCoverUrl());
        videoDetail.put("like_count", video.getLikeCount());
        videoDetail.put("comment_count", video.getCommentCount());
        videoDetail.put("visit_count", video.getVisitCount());
        videoDetail.put("created_at", formatDateTime(video.getCreatedAt()));
        videoDetail.put("updated_at", formatDateTime(video.getUpdatedAt()));
        videoDetail.put("publisher", publisherInfo);
        return videoDetail;
    }

    private Map<String, Object> buildVideoPageData(List<Video> videos, long total, int pageNum, int pageSize) {
        Map<String, Object> data = new HashMap<>();
        data.put("items", buildVideoItems(videos));
        data.put("total", total);
        data.put("pages", calcPages(total, pageSize));
        data.put("current", pageNum);
        data.put("size", pageSize);
        return data;
    }

    private List<Map<String, Object>> buildVideoItems(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> userIds = videos.stream()
                .map(Video::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return videos.stream()
                    .map(this::toVideoItem)
                    .collect(Collectors.toList());
        }

        List<User> users = userMapper.selectByIds(userIds);
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return videos.stream()
                .map(video -> toVideoItem(video, userMap.get(video.getUserId())))
                .collect(Collectors.toList());
    }

    private Map<String, Object> toVideoItem(Video video) {
        return toVideoItem(video, null);
    }

    private Map<String, Object> toVideoItem(Video video, User user) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", video.getId());
        item.put("user_id", user == null ? video.getUserId() : user.getUsername());
        item.put("video_url", video.getVideoUrl());
        item.put("cover_url", video.getCoverUrl());
        item.put("title", video.getTitle());
        item.put("description", video.getDescription());
        item.put("visit_count", defaultInteger(video.getVisitCount()));
        item.put("like_count", defaultInteger(video.getLikeCount()));
        item.put("comment_count", defaultInteger(video.getCommentCount()));
        item.put("audit_status", video.getAuditStatus());
        item.put("audit_reason", video.getAuditReason());
        item.put("created_at", formatDateTime(video.getCreatedAt()));
        item.put("updated_at", formatDateTime(video.getUpdatedAt()));
        item.put("deleted_at", formatDateTime(video.getDeletedAt()));
        return item;
    }

    private List<VideoAuditVO> buildAuditItems(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> userIds = videos.stream().map(Video::getUserId).collect(Collectors.toSet());
        Map<String, User> userMap = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return videos.stream().map(video -> {
            VideoAuditVO item = new VideoAuditVO();
            item.setVideoId(video.getId());
            item.setTitle(video.getTitle());
            item.setDescription(video.getDescription());
            item.setVideoUrl(video.getVideoUrl());
            item.setCoverUrl(video.getCoverUrl());
            item.setAuthorId(video.getUserId());
            User author = userMap.get(video.getUserId());
            item.setAuthorName(author == null ? null : author.getUsername());
            item.setAuditStatus(video.getAuditStatus());
            item.setAuditReason(video.getAuditReason());
            item.setAuditBy(video.getAuditBy());
            item.setAuditAt(video.getAuditAt());
            item.setCreatedAt(video.getCreatedAt());
            return item;
        }).collect(Collectors.toList());
    }

    private String resolveTargetUserId(String accessToken, String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (accessToken != null && !accessToken.isBlank() && authentication != null && authentication.getPrincipal() != null) {
            return authentication.getPrincipal().toString();
        }
        return null;
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private Long authenticatedUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        try {
            return Long.valueOf(principal.toString());
        } catch (NumberFormatException e) {
            return null;
        }
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

    private Integer toInteger(Object value) {
        if (value instanceof Integer number) {
            return number;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME_FORMATTER.format(dateTime);
    }
}
