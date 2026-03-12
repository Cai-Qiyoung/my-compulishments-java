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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            targetUserId = String.valueOf(jwtUtil.getUserIdFromToken(accessToken));
        }else {
            targetUserId = userId;
        }

        Page<Relation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Relation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Relation::getFromUserId, targetUserId)
                .eq(Relation::getStatus, 0);
        IPage<Relation> relationPage = this.page(page, wrapper);

        // 封装返回数据（用户ID、用户名、头像）
        List<Map<String, Object>> items = relationPage.getRecords().stream()
                .map(relation -> {
                    User user = userMapper.selectById(relation.getToUserId());
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", user.getId().toString());
                    item.put("username", user.getUsername());
                    item.put("avatar_url", user.getAvatarUrl());
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
            targetUserId = String.valueOf(jwtUtil.getUserIdFromToken(accessToken));
        }else {
            targetUserId = userId;
        }

        Page<Relation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Relation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Relation::getToUserId, targetUserId)
                .eq(Relation::getStatus, 0);
        IPage<Relation> relationPage = this.page(page, wrapper);

        // 封装返回数据（用户ID、用户名、头像）
        List<Map<String, Object>> items = relationPage.getRecords().stream()
                .map(relation -> {
                    User user = userMapper.selectById(relation.getFromUserId());
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", user.getId().toString());
                    item.put("username", user.getUsername());
                    item.put("avatar_url", user.getAvatarUrl());
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

        // 2. 从 Token 解析用户ID（核心！）
        Long currentUserId = jwtUtil.getUserIdFromToken(accessToken);
        if (currentUserId == null) {
            return ResultVo.fail("登录已过期");
        }
        String targetUserId = currentUserId.toString();

        Page<Relation> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Relation> wrapper = new LambdaQueryWrapper<>();
        // 我关注的人
        wrapper.eq(Relation::getFromUserId, targetUserId);
        wrapper.eq(Relation::getStatus, 0);

        IPage<Relation> relationPage = this.page(page, wrapper);
        List<Relation> followList = relationPage.getRecords();

        List<Map<String, Object>> friendList = new ArrayList<>();
        for (Relation relation : followList) {
            String toUserId = relation.getToUserId();

            // 查询对方是否也关注了我 → 是就是好友
            boolean isMutual = this.lambdaQuery()
                    .eq(Relation::getFromUserId, toUserId)
                    .eq(Relation::getToUserId, targetUserId)
                    .eq(Relation::getStatus, 0)
                    .exists();

            if (isMutual) {
                User user = userMapper.selectById(toUserId);
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