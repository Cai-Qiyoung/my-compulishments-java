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
    private static final String REDIS_POPULAR = "danmaku:video:popular";
    @Autowired
    private JwtUtil jwtUtil;


    @Override
    public ResultVo<?> publishVideo(String accessToken, MultipartFile videoFile, MultipartFile coverFile, String title, String description) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(accessToken);
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
                Long currentUserId = jwtUtil.getUserIdFromToken(accessToken);
                targetUserId = currentUserId.toString();
            }

            // 2. 分页查询该用户发布的视频
            LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Video::getUserId, targetUserId)
                    .orderByDesc(Video::getCreatedAt); // 按发布时间倒序

            Page<Video> page = new Page<>(pageNum, pageSize);
            page(page, wrapper);

            // 3. 给每个视频补全用户名
            List<Video> records = page.getRecords();
            for (Video video : records) {
                User user = userMapper.selectById(video.getUserId());
                if (user != null) {
                    video.setUserId(user.getUsername());
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
        long start = (long) (pageNum - 1) * pageSize;
        long end = start + pageSize - 1;

        Set<ZSetOperations.TypedTuple<Object>> tuples =
                redisUtil.getRedisTemplate().opsForZSet().reverseRangeWithScores(REDIS_POPULAR, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return ResultVo.success(Collections.emptyMap());
        }

        List<Object> ids = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .collect(Collectors.toList());

        List<Video> list = videoMapper.selectBatchIds(ids);
        list.sort((v1, v2) -> {
            Double s1 = redisUtil.getRedisTemplate().opsForZSet().score(REDIS_POPULAR, v1.getId());
            Double s2 = redisUtil.getRedisTemplate().opsForZSet().score(REDIS_POPULAR, v2.getId());
            return Double.compare(
                    s2 != null ? s2 : 0D,
                    s1 != null ? s1 : 0D
            );
        });

        Map<String, Object> map = new HashMap<>();
        map.put("items", list);
        map.put("total", list.size());
        return ResultVo.success(map);
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


    public void incrVisitCount(String videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video != null) {
            video.setVisitCount(video.getVisitCount() + 1);
            videoMapper.updateById(video);
            redisUtil.getRedisTemplate().opsForZSet().incrementScore(REDIS_POPULAR, videoId, 1D);
        }
    }
}