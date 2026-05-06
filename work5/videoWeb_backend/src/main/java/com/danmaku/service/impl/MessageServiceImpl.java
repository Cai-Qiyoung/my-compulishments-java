package com.danmaku.service.impl;

import cn.hutool.core.util.IdUtil;
import com.danmaku.constant.ConversationType;
import com.danmaku.constant.MessageType;
import com.danmaku.constant.RedisKeys;
import com.danmaku.entity.Conversation;
import com.danmaku.entity.ConversationMember;
import com.danmaku.entity.User;
import com.danmaku.exception.BusinessException;
import com.danmaku.mapper.ChatMessageMapper;
import com.danmaku.mapper.ContactBlockMapper;
import com.danmaku.mapper.ConversationMapper;
import com.danmaku.mapper.ConversationMemberMapper;
import com.danmaku.mapper.RelationMapper;
import com.danmaku.mapper.UserMapper;
import com.danmaku.model.ChatMessagePayload;
import com.danmaku.mq.ChatMessageProducer;
import com.danmaku.service.MessageService;
import com.danmaku.util.RedisUtil;
import com.danmaku.vo.ChatMessageVO;
import com.danmaku.vo.ResultVo;
import com.danmaku.websocket.ChatSessionRegistry;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class MessageServiceImpl implements MessageService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_LAST_MESSAGE_LENGTH = 500;
    private static final long STRANGER_FIRST_MESSAGE_TTL_DAYS = 30L;

    @Resource
    private ConversationMapper conversationMapper;
    @Resource
    private ConversationMemberMapper conversationMemberMapper;
    @Resource
    private ContactBlockMapper contactBlockMapper;
    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private RelationMapper relationMapper;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private ChatMessageProducer chatMessageProducer;
    @Resource
    private ChatSessionRegistry chatSessionRegistry;
    @Resource
    private SessionServiceImpl sessionService;

    @Value("${app.chat.message-cache-size:200}")
    private int messageCacheSize;
    @Value("${app.chat.message-cache-ttl-hours:24}")
    private long messageCacheTtlHours;

    @Override
    public ResultVo<?> sendMessage(String accessToken, String conversationId, String messageType, String content) {
        return ResultVo.success(sendMessageByUserId(currentUserId(), conversationId, messageType, content));
    }

    @Override
    public ChatMessageVO sendMessageByUserId(String userId, String conversationId, String messageType, String content) {
        Conversation conversation = requireConversation(conversationId);
        ensureMember(conversationId, userId);
        validateMessageType(messageType);
        if (content == null || content.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }

        List<String> memberUserIds = conversationMemberMapper.selectUserIdsByConversationId(conversationId);
        StrangerMessageRuleAction strangerRuleAction = StrangerMessageRuleAction.none();
        if (ConversationType.SINGLE.equals(conversation.getConversationType())) {
            String targetUserId = memberUserIds.stream()
                    .filter(item -> !item.equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("单聊会话成员异常"));
            if (contactBlockMapper.countActiveBetween(userId, targetUserId) > 0) {
                throw new BusinessException("当前会话已被屏蔽，无法发送消息");
            }
            strangerRuleAction = evaluateStrangerMessageRule(userId, targetUserId);
        }

        User sender = userMapper.selectById(userId);
        if (sender == null) {
            throw new BusinessException("发送用户不存在");
        }

        ChatMessagePayload payload = new ChatMessagePayload();
        payload.setMessageId(IdUtil.getSnowflakeNextIdStr());
        payload.setConversationId(conversationId);
        payload.setSenderId(userId);
        payload.setMessageType(messageType);
        payload.setContent(content.trim());
        payload.setSentAt(LocalDateTime.now());

        ChatMessageVO baseMessage = toMessageVO(payload, sender);
        cacheRecentMessage(baseMessage);
        conversationMapper.updateLastMessage(
                conversationId,
                buildLastMessagePreview(payload.getContent(), messageType),
                messageType,
                payload.getSentAt()
        );
        sessionService.invalidateSessionCaches(memberUserIds);
        chatMessageProducer.send(payload);
        pushMessage(memberUserIds, baseMessage, userId);
        applyStrangerMessageRuleAction(strangerRuleAction);
        return withSelf(baseMessage, true);
    }

    @Override
    public ResultVo<?> getHistory(String accessToken, String conversationId, String startTime, String endTime, Integer pageNum, Integer pageSize) {
        String userId = currentUserId();
        ensureMember(conversationId, userId);
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        if (safePageNum == 1 && start == null && end == null) {
            List<ChatMessageVO> cachedItems = getRecentMessages(conversationId, userId, safePageSize);
            if (!cachedItems.isEmpty()) {
                long total = Math.max(chatMessageMapper.countHistory(conversationId, null, null), cachedItems.size());
                Map<String, Object> data = buildHistoryData(cachedItems, total, safePageNum, safePageSize);
                return ResultVo.success(data);
            }
        }

        long total = chatMessageMapper.countHistory(conversationId, start, end);
        List<ChatMessageVO> items = total == 0
                ? Collections.emptyList()
                : chatMessageMapper.selectHistory(conversationId, start, end, calcOffset(safePageNum, safePageSize), safePageSize);
        for (ChatMessageVO item : items) {
            item.setSelf(Objects.equals(userId, item.getSenderId()));
        }
        Map<String, Object> data = buildHistoryData(items, total, safePageNum, safePageSize);
        return ResultVo.success(data);
    }

    private void pushMessage(Collection<String> userIds, ChatMessageVO baseMessage, String senderUserId) {
        for (String userId : userIds) {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("event", "MESSAGE");
            envelope.put("data", withSelf(baseMessage, userId.equals(senderUserId)));
            chatSessionRegistry.sendToUser(userId, envelope);
        }
    }

    private List<ChatMessageVO> getRecentMessages(String conversationId, String userId, int pageSize) {
        List<ChatMessageVO> cached = redisUtil.getListRange(RedisKeys.messageRecentList(conversationId), 0, -1);
        if (cached == null || cached.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessageVO> result = new ArrayList<>();
        for (int index = cached.size() - 1; index >= 0 && result.size() < pageSize; index--) {
            ChatMessageVO item = cached.get(index);
            if (item == null) {
                continue;
            }
            result.add(withSelf(item, userId.equals(item.getSenderId())));
        }
        return result;
    }

    private void cacheRecentMessage(ChatMessageVO message) {
        String cacheKey = RedisKeys.messageRecentList(message.getConversationId());
        long size = redisUtil.rightPush(cacheKey, withSelf(message, null));
        if (size > messageCacheSize) {
            redisUtil.trim(cacheKey, size - messageCacheSize, size - 1);
        }
        redisUtil.expire(cacheKey, messageCacheTtlHours, TimeUnit.HOURS);
        redisUtil.deleteByPrefix(RedisKeys.messageHistoryPrefix(message.getConversationId()));
    }

    private Map<String, Object> buildHistoryData(List<ChatMessageVO> items, long total, int pageNum, int pageSize) {
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("current", pageNum);
        data.put("size", pageSize);
        data.put("pages", pageSize <= 0 ? 0L : (total + pageSize - 1) / pageSize);
        return data;
    }

    private ChatMessageVO toMessageVO(ChatMessagePayload payload, User sender) {
        ChatMessageVO message = new ChatMessageVO();
        message.setMessageId(payload.getMessageId());
        message.setConversationId(payload.getConversationId());
        message.setSenderId(payload.getSenderId());
        message.setSenderName(sender.getUsername());
        message.setSenderAvatar(sender.getAvatarUrl());
        message.setMessageType(payload.getMessageType());
        message.setContent(payload.getContent());
        message.setSentAt(payload.getSentAt());
        return message;
    }

    private ChatMessageVO withSelf(ChatMessageVO source, Boolean self) {
        ChatMessageVO target = new ChatMessageVO();
        target.setMessageId(source.getMessageId());
        target.setConversationId(source.getConversationId());
        target.setSenderId(source.getSenderId());
        target.setSenderName(source.getSenderName());
        target.setSenderAvatar(source.getSenderAvatar());
        target.setMessageType(source.getMessageType());
        target.setContent(source.getContent());
        target.setSentAt(source.getSentAt());
        target.setSelf(self);
        return target;
    }

    private void ensureMember(String conversationId, String userId) {
        ConversationMember member = conversationMemberMapper.selectByConversationIdAndUserId(conversationId, userId);
        if (member == null) {
            throw new BusinessException("无权限访问该会话");
        }
    }

    private Conversation requireConversation(String conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }
        return conversation;
    }

    private void validateMessageType(String messageType) {
        if (!MessageType.TEXT.equals(messageType) && !MessageType.IMAGE.equals(messageType)) {
            throw new BusinessException("暂不支持该消息类型");
        }
    }

    private String buildLastMessagePreview(String content, String messageType) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim().replace("\r\n", "\n").replace('\r', '\n');
        if (MessageType.IMAGE.equals(messageType)) {
            return normalized.length() <= MAX_LAST_MESSAGE_LENGTH
                    ? normalized
                    : normalized.substring(0, MAX_LAST_MESSAGE_LENGTH);
        }
        if (normalized.length() <= MAX_LAST_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LAST_MESSAGE_LENGTH - 1) + "…";
    }

    private StrangerMessageRuleAction evaluateStrangerMessageRule(String senderUserId, String targetUserId) {
        if (relationMapper.countMutualFollow(senderUserId, targetUserId) > 0) {
            return StrangerMessageRuleAction.none();
        }
        String limitKey = RedisKeys.strangerFirstMessage(senderUserId, targetUserId);
        if (redisUtil.getCacheObjectSafely(limitKey) != null) {
            throw new BusinessException("非好友之间，在对方回复前仅可发送一条消息");
        }
        String reverseLimitKey = RedisKeys.strangerFirstMessage(targetUserId, senderUserId);
        if (redisUtil.getCacheObjectSafely(reverseLimitKey) != null) {
            return StrangerMessageRuleAction.clearReverse(reverseLimitKey);
        }
        return StrangerMessageRuleAction.markPending(limitKey);
    }

    private void applyStrangerMessageRuleAction(StrangerMessageRuleAction action) {
        if (action == null) {
            return;
        }
        if (action.getReverseKeyToDelete() != null) {
            redisUtil.deleteObject(action.getReverseKeyToDelete());
        }
        if (action.getPendingKeyToSet() != null) {
            redisUtil.setCacheObject(
                    action.getPendingKeyToSet(),
                    1,
                    STRANGER_FIRST_MESSAGE_TTL_DAYS,
                    TimeUnit.DAYS
            );
        }
    }

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
    }

    private int calcOffset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace(" ", "T");
        try {
            return LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("时间格式错误，需为 ISO_LOCAL_DATE_TIME");
        }
    }

    private static final class StrangerMessageRuleAction {
        private final String pendingKeyToSet;
        private final String reverseKeyToDelete;

        private StrangerMessageRuleAction(String pendingKeyToSet, String reverseKeyToDelete) {
            this.pendingKeyToSet = pendingKeyToSet;
            this.reverseKeyToDelete = reverseKeyToDelete;
        }

        public static StrangerMessageRuleAction none() {
            return new StrangerMessageRuleAction(null, null);
        }

        public static StrangerMessageRuleAction markPending(String pendingKeyToSet) {
            return new StrangerMessageRuleAction(pendingKeyToSet, null);
        }

        public static StrangerMessageRuleAction clearReverse(String reverseKeyToDelete) {
            return new StrangerMessageRuleAction(null, reverseKeyToDelete);
        }

        public String getPendingKeyToSet() {
            return pendingKeyToSet;
        }

        public String getReverseKeyToDelete() {
            return reverseKeyToDelete;
        }
    }
}
