package com.danmaku.service.impl;

import cn.hutool.core.util.IdUtil;
import com.danmaku.constant.ConversationMemberRole;
import com.danmaku.constant.ConversationType;
import com.danmaku.constant.RedisKeys;
import com.danmaku.entity.Conversation;
import com.danmaku.entity.ConversationMember;
import com.danmaku.entity.User;
import com.danmaku.mapper.ContactBlockMapper;
import com.danmaku.mapper.ConversationMapper;
import com.danmaku.mapper.ConversationMemberMapper;
import com.danmaku.mapper.UserMapper;
import com.danmaku.service.SessionService;
import com.danmaku.util.RedisUtil;
import com.danmaku.vo.ResultVo;
import com.danmaku.vo.SessionItemVO;
import jakarta.annotation.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SessionServiceImpl implements SessionService {
    private static final long SESSION_CACHE_TTL_MINUTES = 3L;
    private static final long SESSION_CREATE_LOCK_TTL_SECONDS = 5L;

    @Resource
    private ConversationMapper conversationMapper;
    @Resource
    private ConversationMemberMapper conversationMemberMapper;
    @Resource
    private ContactBlockMapper contactBlockMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> createSingleSession(String accessToken, String targetUserId) {
        String userId = currentUserId();
        if (userId.equals(targetUserId)) {
            return ResultVo.fail("不能和自己发起会话");
        }
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            return ResultVo.fail("目标用户不存在");
        }
        if (contactBlockMapper.countActiveBetween(userId, targetUserId) > 0) {
            return ResultVo.fail("当前联系人已被屏蔽，无法创建会话");
        }

        String bizKey = buildSingleBizKey(userId, targetUserId);
        String lockKey = RedisKeys.sessionCreateLock(userId, bizKey);
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(lockKey, lockValue, SESSION_CREATE_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            return ResultVo.fail("会话创建过于频繁，请稍后重试");
        }

        try {
            Conversation existed = conversationMapper.selectByBizKey(bizKey);
            if (existed != null) {
                return ResultVo.success(buildSessionResult(existed.getId(), true));
            }

            Conversation conversation = new Conversation();
            conversation.setId(IdUtil.getSnowflakeNextIdStr());
            conversation.setConversationType(ConversationType.SINGLE);
            conversation.setBizKey(bizKey);
            conversation.setCreatorId(userId);
            conversation.setCreatedAt(LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.insert(conversation);

            List<ConversationMember> members = new ArrayList<>();
            members.add(buildMember(conversation.getId(), userId, ConversationMemberRole.OWNER));
            members.add(buildMember(conversation.getId(), targetUserId, ConversationMemberRole.MEMBER));
            conversationMemberMapper.insertBatch(members);
            invalidateSessionCaches(List.of(userId, targetUserId));
            return ResultVo.success(buildSessionResult(conversation.getId(), false));
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> createGroupSession(String accessToken, String groupName, List<String> memberIds) {
        String userId = currentUserId();
        Set<String> normalizedMembers = new LinkedHashSet<>();
        normalizedMembers.add(userId);
        if (memberIds != null) {
            normalizedMembers.addAll(memberIds);
        }
        normalizedMembers.remove(null);
        normalizedMembers.remove("");
        if (normalizedMembers.size() < 3) {
            return ResultVo.fail("群聊至少需要 3 个成员");
        }

        List<User> users = userMapper.selectByIds(normalizedMembers);
        if (users.size() != normalizedMembers.size()) {
            return ResultVo.fail("存在无效的群成员");
        }

        Conversation conversation = new Conversation();
        conversation.setId(IdUtil.getSnowflakeNextIdStr());
        conversation.setConversationType(ConversationType.GROUP);
        conversation.setName(groupName == null || groupName.isBlank() ? "未命名群聊" : groupName.trim());
        conversation.setCreatorId(userId);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conversation);

        List<ConversationMember> members = new ArrayList<>();
        for (String memberId : normalizedMembers) {
            members.add(buildMember(
                    conversation.getId(),
                    memberId,
                    userId.equals(memberId) ? ConversationMemberRole.OWNER : ConversationMemberRole.MEMBER
            ));
        }
        conversationMemberMapper.insertBatch(members);
        invalidateSessionCaches(normalizedMembers);
        return ResultVo.success(buildSessionResult(conversation.getId(), false));
    }

    @Override
    public ResultVo<?> getSessionList(String accessToken, Integer pageNum, Integer pageSize) {
        String userId = currentUserId();
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        String cacheKey = RedisKeys.sessionList(userId, safePageNum, safePageSize);
        Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
        if (cached != null) {
            return ResultVo.success(cached);
        }

        long total = conversationMapper.countUserSessions(userId);
        List<SessionItemVO> items = total == 0
                ? List.of()
                : conversationMapper.selectUserSessions(userId, calcOffset(safePageNum, safePageSize), safePageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("current", safePageNum);
        data.put("size", safePageSize);
        data.put("pages", calcPages(total, safePageSize));
        redisUtil.setCacheObjectSafely(cacheKey, data, SESSION_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(data);
    }

    public void invalidateSessionCaches(Collection<String> userIds) {
        for (String userId : userIds) {
            redisUtil.deleteByPrefix(RedisKeys.sessionListPrefix(userId));
        }
    }

    private Map<String, Object> buildSessionResult(String conversationId, boolean existed) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversation_id", conversationId);
        data.put("existed", existed);
        return data;
    }

    private ConversationMember buildMember(String conversationId, String userId, String role) {
        ConversationMember member = new ConversationMember();
        member.setId(IdUtil.getSnowflakeNextIdStr());
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberRole(role);
        member.setStatus(0);
        member.setJoinedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        return member;
    }

    private String buildSingleBizKey(String leftUserId, String rightUserId) {
        if (leftUserId.compareTo(rightUserId) < 0) {
            return ConversationType.SINGLE + ":" + leftUserId + ":" + rightUserId;
        }
        return ConversationType.SINGLE + ":" + rightUserId + ":" + leftUserId;
    }

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
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
        return pageSize <= 0 ? 0L : (total + pageSize - 1) / pageSize;
    }
}
