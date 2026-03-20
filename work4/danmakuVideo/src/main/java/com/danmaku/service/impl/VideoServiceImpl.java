package com.danmaku.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danmaku.entity.User;
import com.danmaku.entity.Video;
import com.danmaku.mapper.UserMapper;
import com.danmaku.mapper.VideoMapper;
import com.danmaku.service.VideoService;
import com.danmaku.util.FileUploadUtil;
import com.danmaku.util.JwtUtil;
import com.danmaku.util.RedisUtil;
import com.danmaku.vo.ResultVo;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Resource
    private VideoMapper videoMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private FileUploadUtil fileUploadUtil;
    static final String REDIS_POPULAR = "danmaku:video:popular";
    @Autowired
    private JwtUtil jwtUtil;


    @Override
    public ResultVo<?> publishVideo(String accessToken, MultipartFile videoFile, MultipartFile coverFile, String title, String description) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String videoUrl = fileUploadUtil.uploadVideo(videoFile);
            String coverUrl = fileUploadUtil.uploadCover(coverFile);

            Video video = new Video();
            video.setUserId(String.valueOf(userId));
            video.setTitle(title);
            video.setDescription(description);
            video.setVideoUrl(videoUrl);
            video.setCoverUrl(coverUrl);
            video.setCreatedAt(LocalDateTime.now());
            video.setUpdatedAt(LocalDateTime.now());
            videoMapper.insert(video);

            redisUtil.getRedisTemplate().opsForZSet().add(REDIS_POPULAR, video.getId(), 0D);

            return ResultVo.success("发布成功！");
        } catch (Exception e) {
            throw new RuntimeException("投稿失败", e);
        }
    }

    @Override
    public ResultVo<Map<String, Object>> getVideoList(String accessToken, String userId, Integer pageNum, Integer pageSize) {
        try {
            // 1. 确定目标用户ID
            String targetUserId;
            if (userId != null && !userId.isEmpty()) {
                // 传了user_id → 查看他人主页
                targetUserId = userId;
            } else {
                // 没传user_id → 从token解析自己的ID
                if (accessToken == null || accessToken.isEmpty()) {
                    return ResultVo.fail("未登录且未指定用户ID");
                }
                Long currentUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                targetUserId = currentUserId.toString();
            }

            // 2. 分页查询该用户发布的视频
            LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Video::getUserId, targetUserId)
                    .orderByDesc(Video::getCreatedAt); // 按发布时间倒序

            Page<Video> page = new Page<>(pageNum, pageSize);
            page(page, wrapper);

            // 3. 优化：批量查询用户名（解决N+1）
            List<Video> records = page.getRecords();
            if (!records.isEmpty()) {
                // 提取所有用户ID（这里其实只有targetUserId，但保留批量逻辑适配扩展）
                Set<String> userIds = records.stream()
                        .map(Video::getUserId)
                        .collect(Collectors.toSet());
                List<User> users = userMapper.selectBatchIds(userIds);
                Map<String, User> userMap = users.stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

                // 批量补全用户名
                for (Video video : records) {
                    User user = userMap.get(video.getUserId());
                    if (user != null) {
                        video.setUserId(user.getUsername());
                    }
                }
            }

            // 4. 封装分页返回
            Map<String, Object> data = new HashMap<>();
            data.put("items", records);
            data.put("total", page.getTotal());
            data.put("pages", page.getPages());
            data.put("current", page.getCurrent());
            data.put("size", page.getSize());

            return ResultVo.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("获取发布列表失败：" + e.getMessage());
        }
    }

    @Override
    public ResultVo<Map<String, Object>> getPopularVideo(Integer pageNum, Integer pageSize) {
        try {
            // 1. 分页参数校验
            if (pageNum == null || pageNum < 1) pageNum = 1;
            if (pageSize == null || pageSize < 1) pageSize = 10;

            long start = (long) (pageNum - 1) * pageSize;
            long end = start + pageSize - 1;

            // 2. 从Redis获取热门视频ID
            Set<ZSetOperations.TypedTuple<Object>> tuples =
                    redisUtil.getRedisTemplate().opsForZSet().reverseRangeWithScores(REDIS_POPULAR, start, end);

            if (tuples == null || tuples.isEmpty()) {
                Map<String, Object> emptyMap = new HashMap<>();
                emptyMap.put("items", Collections.emptyList());
                emptyMap.put("total", 0L);
                return ResultVo.success(emptyMap);
            }

            List<String> videoIds = tuples.stream()
                    .map(ZSetOperations.TypedTuple::getValue)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());

            // 3. 查询数据库（现在类型完全匹配）
            List<Video> videos = videoMapper.selectBatchIds(videoIds);

            // 4. 按Redis热度排序
            videos.sort((v1, v2) -> {
                Double s1 = redisUtil.getRedisTemplate().opsForZSet().score(REDIS_POPULAR, v1.getId());
                Double s2 = redisUtil.getRedisTemplate().opsForZSet().score(REDIS_POPULAR, v2.getId());
                return Double.compare(
                        s2 != null ? s2 : 0D,
                        s1 != null ? s1 : 0D
                );
            });

            // 5. 封装结果
            Map<String, Object> result = new HashMap<>();
            result.put("items", videos);
            result.put("total", redisUtil.getRedisTemplate().opsForZSet().zCard(REDIS_POPULAR));
            return ResultVo.success(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("获取热门视频失败：" + e.getMessage());
        }
    }

    @Override
    public ResultVo<?> searchVideo(String keywords, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();

        if (keywords != null && !keywords.trim().isEmpty()) {
            wrapper.and(w -> w
                    .like(Video::getTitle, keywords)         // 匹配标题
                    .or()
                    .like(Video::getDescription, keywords)   // 匹配描述
                    .or()
                    .like(Video::getUserId, keywords)      // 匹配用户
            );
        }

        // 分页
        Page<Video> page = new Page<>(pageNum, pageSize);
        page(page, wrapper);

        // 封装返回
        Map<String, Object> data = new HashMap<>();
        data.put("items", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pages", page.getPages());
        data.put("current", page.getCurrent());
        data.put("size", page.getSize());

        return ResultVo.success(data);
    }

}