package com.danmaku.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danmaku.entity.Relation;
import com.danmaku.entity.User;
import com.danmaku.mapper.RelationMapper;
import com.danmaku.mapper.UserMapper;
import com.danmaku.service.RelationService;
import com.danmaku.util.JwtUtil;
import com.danmaku.vo.ResultVo;
import jakarta.annotation.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RelationServiceImpl extends ServiceImpl<RelationMapper, Relation> implements RelationService {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> followUser(String accessToken, String toUserId) {
        Long fromId = jwtUtil.getUserIdFromToken(accessToken);
        String fromUserId = String.valueOf(fromId);
        if (fromUserId.equals(toUserId) ) {
            return ResultVo.fail("不能关注自己");
        }

        LambdaQueryWrapper<Relation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Relation::getFromUserId, fromUserId)
                .eq(Relation::getToUserId, toUserId);
        Relation relation = getOne(wrapper);

        if (relation == null) {
            Relation rel = new Relation();
            rel.setFromUserId(fromUserId);
            rel.setToUserId(toUserId);
            rel.setStatus(0);
            save(rel);
            return ResultVo.success("关注成功");
        } else {
            // 切换状态：关注 <-> 取关
            relation.setStatus(relation.getStatus() == 0 ? 1 : 0);
            updateById(relation);
            return relation.getStatus() == 0 ? ResultVo.success("关注成功") : ResultVo.success("已取关");
        }
    }

    @Override
    public ResultVo<?> getFollowList(String userId, String accessToken ,Integer pageNum, Integer pageSize) {
        // 确定目标用户ID
        String targetUserId;
        if(userId==null){
            Long currentUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            targetUserId = currentUserId.toString();
        }else {
            targetUserId = userId;
        }

        Page<Relation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Relation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Relation::getFromUserId, targetUserId)
                .eq(Relation::getStatus, 0);
        IPage<Relation> relationPage = this.page(page, wrapper);

        // 优化：批量查询用户信息（解决N+1）
        List<Relation> relations = relationPage.getRecords();
        if (relations.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("items", Collections.emptyList());
            data.put("total", 0);
            return ResultVo.success(data);
        }

        // 提取所有被关注用户ID
        Set<String> toUserIds = relations.stream()
                .map(Relation::getToUserId)
                .collect(Collectors.toSet());
        // 批量查询用户
        List<User> users = userMapper.selectBatchIds(toUserIds);
        // 构建用户ID -> 用户信息的Map
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 封装返回数据（用户ID、用户名、头像）
        List<Map<String, Object>> items = relations.stream()
                .map(relation -> {
                    User user = userMap.get(relation.getToUserId());
                    Map<String, Object> item = new HashMap<>();
                    if (user != null) {
                        item.put("id", user.getId().toString());
                        item.put("username", user.getUsername());
                        item.put("avatar_url", user.getAvatarUrl());
                    }
                    return item;
                })
                .collect(Collectors.toList());

        // 组装最终返回格式
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", relationPage.getTotal());

        return ResultVo.success(data);
    }

    @Override
    public ResultVo<?> getFansList(String userId , String accessToken , Integer pageNum, Integer pageSize) {
        // 确定目标用户ID
        String targetUserId;
        if(userId==null){
            Long currentUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            targetUserId = currentUserId.toString();
        }else {
            targetUserId = userId;
        }

        Page<Relation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Relation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Relation::getToUserId, targetUserId)
                .eq(Relation::getStatus, 0);
        IPage<Relation> relationPage = this.page(page, wrapper);

        // 优化：批量查询用户信息（解决N+1）
        List<Relation> relations = relationPage.getRecords();
        if (relations.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("items", Collections.emptyList());
            data.put("total", 0);
            return ResultVo.success(data);
        }

        // 提取所有粉丝用户ID
        Set<String> fromUserIds = relations.stream()
                .map(Relation::getFromUserId)
                .collect(Collectors.toSet());
        // 批量查询用户
        List<User> users = userMapper.selectBatchIds(fromUserIds);
        // 构建用户ID -> 用户信息的Map
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 封装返回数据（用户ID、用户名、头像）
        List<Map<String, Object>> items = relations.stream()
                .map(relation -> {
                    User user = userMap.get(relation.getFromUserId());
                    Map<String, Object> item = new HashMap<>();
                    if (user != null) {
                        item.put("id", user.getId().toString());
                        item.put("username", user.getUsername());
                        item.put("avatar_url", user.getAvatarUrl());
                    }
                    return item;
                })
                .collect(Collectors.toList());

        // 组装最终返回格式
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", relationPage.getTotal());

        return ResultVo.success(data);
    }

    @Override
    public ResultVo<?> getFriendList(String accessToken, Integer pageNum, Integer pageSize) {
        if (accessToken == null || accessToken.isBlank()) {
            return ResultVo.fail("请先登录");
        }

        Long currentUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (currentUserId == null) {
            return ResultVo.fail("登录已过期");
        }
        String targetUserId = currentUserId.toString();

        Page<Relation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Relation> wrapper = new LambdaQueryWrapper<>();
        // 我关注的人
        wrapper.eq(Relation::getFromUserId, targetUserId)
                .eq(Relation::getStatus, 0);

        IPage<Relation> relationPage = this.page(page, wrapper);
        List<Relation> followList = relationPage.getRecords();

        if (followList.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("items", Collections.emptyList());
            data.put("total", 0);
            return ResultVo.success(data);
        }

        // 优化1：批量查询互相关注关系
        Set<String> toUserIds = followList.stream()
                .map(Relation::getToUserId)
                .collect(Collectors.toSet());
        // 批量查询对方是否关注我
        LambdaQueryWrapper<Relation> mutualWrapper = new LambdaQueryWrapper<>();
        mutualWrapper.eq(Relation::getStatus, 0)
                .in(Relation::getFromUserId, toUserIds)
                .eq(Relation::getToUserId, targetUserId);
        List<Relation> mutualRelations = this.list(mutualWrapper);
        Set<String> mutualUserIds = mutualRelations.stream()
                .map(Relation::getFromUserId)
                .collect(Collectors.toSet());

        // 优化2：批量查询用户信息
        List<User> users = userMapper.selectBatchIds(mutualUserIds);
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 组装好友列表
        List<Map<String, Object>> friendList = new ArrayList<>();
        for (String mutualUserId : mutualUserIds) {
            User user = userMap.get(mutualUserId);
            if (user != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", user.getId().toString());
                item.put("username", user.getUsername());
                item.put("avatar_url", user.getAvatarUrl());
                friendList.add(item);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("items", friendList);
        data.put("total", friendList.size());

        return ResultVo.success(data);
    }
}