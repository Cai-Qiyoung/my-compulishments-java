package com.danmaku.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;

import java.util.*;
import java.util.stream.Collectors;

import static com.danmaku.service.impl.VideoServiceImpl.REDIS_POPULAR;

@Service
public class LikeServiceImpl extends ServiceImpl<LikeMapper, Like> implements LikeService {
    @Resource
    private VideoMapper videoMapper;
    @Resource
    private CommentMapper commentMapper;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> likeVideo(String accessToken, String videoId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        Long userId = jwtUtil.getUserIdFromToken(accessToken);
        wrapper.eq(Like::getUserId, String.valueOf(userId)).eq(Like::getVideoId, videoId).eq(Like::getType, 1);
        Like like = getOne(wrapper);

        if (like == null) {
            // 点赞
            Like newLike = new Like();
            newLike.setUserId(String.valueOf(userId));
            newLike.setVideoId(videoId);
            newLike.setType(1);
            save(newLike);

            Video video = videoMapper.selectById(videoId);
            video.setLikeCount(video.getLikeCount() + 1);
            videoMapper.updateById(video);
            incrVisitCount(videoId);

            return ResultVo.success("点赞成功");
        } else {
            // 取消点赞
            remove(wrapper);
            Video video = videoMapper.selectById(videoId);
            video.setLikeCount(Math.max(0, video.getLikeCount() - 1));
            videoMapper.updateById(video);
            return ResultVo.success("取消点赞成功");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> likeComment(String accessToken, String commentId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        Long userId = jwtUtil.getUserIdFromToken(accessToken);
        wrapper.eq(Like::getUserId, String.valueOf(userId)).eq(Like::getCommentId, commentId).eq(Like::getType, 2);
        Like like = getOne(wrapper);

        if (like == null) {
            Like newLike = new Like();
            newLike.setUserId(String.valueOf(userId));
            newLike.setCommentId(commentId);
            newLike.setType(2);
            save(newLike);

            Comment comment = commentMapper.selectById(commentId);
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentMapper.updateById(comment);
            return ResultVo.success("点赞成功");
        } else {
            remove(wrapper);
            Comment comment = commentMapper.selectById(commentId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            commentMapper.updateById(comment);
            return ResultVo.success("取消点赞成功");
        }
    }

    @Override
    public ResultVo<?> likeList(String accessToken, String userId, Integer page_num, Integer page_size) {
        try {
            // 1. 确定目标用户ID
            String targetUserId;
            if (userId != null && !userId.isEmpty()) {
                // 传了user_id → 查看他人主页
                targetUserId = userId;
            } else {

                if (accessToken == null || accessToken.isEmpty()) {
                    return ResultVo.fail("未登录且未指定用户ID");
                }
                Long currentUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                targetUserId = currentUserId.toString();
            }

            // 2. 分页查询 点赞记录
            LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Like::getUserId, targetUserId); // 只查当前用户的喜欢

            Page<Like> page = new Page<>(page_num, page_size);
            page(page, wrapper);

            // 3. 优化：批量查询视频信息（解决N+1）
            List<Like> likeList = page.getRecords();
            if (!likeList.isEmpty()) {
                // 提取所有视频ID
                Set<String> videoIds = likeList.stream()
                        .map(Like::getVideoId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                // 批量查询视频
                List<Video> videos = videoMapper.selectBatchIds(videoIds);
                Map<String, Video> videoMap = videos.stream()
                        .collect(Collectors.toMap(Video::getId, video -> video));

                // 批量设置视频信息
                for (Like like : likeList) {
                    like.setVideo(videoMap.get(like.getVideoId()));
                }
            }

            // 4. 封装分页返回
            Map<String, Object> data = new HashMap<>();
            data.put("items", likeList);
            data.put("total", page.getTotal());

            return ResultVo.success(data);

        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("获取喜欢列表失败：" + e.getMessage());
        }
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