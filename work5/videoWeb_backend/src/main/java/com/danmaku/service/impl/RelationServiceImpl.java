package com.danmaku.service.impl;

import cn.hutool.core.util.IdUtil;
import com.danmaku.constant.RedisKeys;
import com.danmaku.entity.Relation;
import com.danmaku.entity.User;
import com.danmaku.mapper.RelationMapper;
import com.danmaku.mapper.UserMapper;
import com.danmaku.service.ContactService;
import com.danmaku.service.RelationService;
import com.danmaku.util.JwtUtil;
import com.danmaku.util.RedisUtil;
import com.danmaku.vo.ResultVo;
import jakarta.annotation.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RelationServiceImpl implements RelationService {
    private static final long RELATION_LIST_CACHE_TTL_MINUTES = 5L;
    private static final long FOLLOW_LOCK_TTL_SECONDS = 5L;

    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private UserMapper userMapper;
    @Resource
    private RelationMapper relationMapper;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private ContactService contactService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> followUser(String accessToken, String toUserId) {
        Long fromId = jwtUtil.getUserIdFromToken(accessToken);
        String fromUserId = String.valueOf(fromId);
        if (fromUserId.equals(toUserId)) {
            return ResultVo.fail("不能关注自己");
        }
        if (userMapper.selectById(toUserId) == null) {
            return ResultVo.fail("目标用户不存在");
        }

        String lockKey = RedisKeys.followLock(fromUserId, toUserId);
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(lockKey, lockValue, FOLLOW_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            return ResultVo.fail("操作过于频繁，请稍后重试");
        }

        try {
            Relation relation = relationMapper.selectByUsers(fromUserId, toUserId);

            ResultVo<?> result;
            if (relation == null) {
                Relation rel = new Relation();
                rel.setId(IdUtil.getSnowflakeNextIdStr());
                rel.setFromUserId(fromUserId);
                rel.setToUserId(toUserId);
                rel.setStatus(0);
                relationMapper.insert(rel);
                result = ResultVo.success("关注成功");
            } else {
                int nextStatus = relation.getStatus() == 0 ? 1 : 0;
                relationMapper.updateStatusById(relation.getId(), nextStatus);
                result = nextStatus == 0 ? ResultVo.success("关注成功") : ResultVo.success("已取关");
            }

            invalidateRelationCaches(fromUserId, toUserId);
            return result;
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    @Override
    public ResultVo<?> getFollowList(String userId, String accessToken, Integer pageNum, Integer pageSize) {
        String targetUserId = resolveTargetUserId(userId);
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        String cacheKey = RedisKeys.followList(targetUserId, safePageNum, safePageSize);
        Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
        if (cached != null) {
            return ResultVo.success(cached);
        }

        long total = relationMapper.countFollowUsers(targetUserId);
        List<User> users = total == 0
                ? Collections.emptyList()
                : relationMapper.selectFollowUsers(targetUserId, calcOffset(safePageNum, safePageSize), safePageSize);

        Map<String, Object> data = buildUserListResult(users, total, safePageNum, safePageSize);
        redisUtil.setCacheObjectSafely(cacheKey, data, RELATION_LIST_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(data);
    }

    @Override
    public ResultVo<?> getFansList(String userId, String accessToken, Integer pageNum, Integer pageSize) {
        String targetUserId = resolveTargetUserId(userId);
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        String cacheKey = RedisKeys.fansList(targetUserId, safePageNum, safePageSize);
        Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
        if (cached != null) {
            return ResultVo.success(cached);
        }

        long total = relationMapper.countFansUsers(targetUserId);
        List<User> users = total == 0
                ? Collections.emptyList()
                : relationMapper.selectFansUsers(targetUserId, calcOffset(safePageNum, safePageSize), safePageSize);

        Map<String, Object> data = buildUserListResult(users, total, safePageNum, safePageSize);
        redisUtil.setCacheObjectSafely(cacheKey, data, RELATION_LIST_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(data);
    }

    @Override
    public ResultVo<?> getFriendList(String accessToken, Integer pageNum, Integer pageSize) {
        return contactService.getContactList(accessToken, pageNum, pageSize);
    }

    private Map<String, Object> buildUserListResult(List<User> users, long total, int pageNum, int pageSize) {
        Map<String, Object> data = new HashMap<>();
        data.put("items", buildUserItems(users));
        data.put("total", total);
        data.put("current", pageNum);
        data.put("size", pageSize);
        data.put("pages", calcPages(total, pageSize));
        return data;
    }

    private List<Map<String, Object>> buildUserItems(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (User user : users) {
            if (user == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("avatar_url", user.getAvatarUrl());
            items.add(item);
        }
        return items;
    }

    private void invalidateRelationCaches(String fromUserId, String toUserId) {
        redisUtil.deleteByPrefix(RedisKeys.followListPrefix(fromUserId));
        redisUtil.deleteByPrefix(RedisKeys.fansListPrefix(toUserId));
        redisUtil.deleteByPrefix(RedisKeys.friendListPrefix(fromUserId));
        redisUtil.deleteByPrefix(RedisKeys.friendListPrefix(toUserId));
    }

    private String resolveTargetUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        Long currentUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return currentUserId.toString();
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
}
