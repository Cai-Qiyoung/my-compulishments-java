package com.danmaku.constant;

public final class RedisKeys {
    private RedisKeys() {
    }

    public static final String VIDEO_POPULAR_ZSET = "danmaku:video:popular";
    private static final String CACHE_PREFIX = "danmaku:cache:";
    private static final String LOCK_PREFIX = "danmaku:lock:";

    public static String videoDetail(String videoId) {
        return CACHE_PREFIX + "video:detail:" + videoId;
    }

    public static String videoList(String userId, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "video:list:" + userId + ":" + pageNum + ":" + pageSize;
    }

    public static String videoListPrefix(String userId) {
        return CACHE_PREFIX + "video:list:" + userId + ":";
    }

    public static String popularVideo(Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "video:popular:" + pageNum + ":" + pageSize;
    }

    public static String popularVideoPrefix() {
        return CACHE_PREFIX + "video:popular:";
    }

    public static String commentList(String videoId, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "comment:list:" + videoId + ":" + pageNum + ":" + pageSize;
    }

    public static String commentListPrefix(String videoId) {
        return CACHE_PREFIX + "comment:list:" + videoId + ":";
    }

    public static String userInfo(String userId) {
        return CACHE_PREFIX + "user:info:" + userId;
    }

    public static String likeList(String userId, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "like:list:" + userId + ":" + pageNum + ":" + pageSize;
    }

    public static String likeListPrefix(String userId) {
        return CACHE_PREFIX + "like:list:" + userId + ":";
    }

    public static String followList(String userId, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "relation:follow:list:" + userId + ":" + pageNum + ":" + pageSize;
    }

    public static String followListPrefix(String userId) {
        return CACHE_PREFIX + "relation:follow:list:" + userId + ":";
    }

    public static String fansList(String userId, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "relation:fans:list:" + userId + ":" + pageNum + ":" + pageSize;
    }

    public static String fansListPrefix(String userId) {
        return CACHE_PREFIX + "relation:fans:list:" + userId + ":";
    }

    public static String friendList(String userId, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "relation:friend:list:" + userId + ":" + pageNum + ":" + pageSize;
    }

    public static String friendListPrefix(String userId) {
        return CACHE_PREFIX + "relation:friend:list:" + userId + ":";
    }

    public static String registerLock(String username) {
        return LOCK_PREFIX + "user:register:" + username;
    }

    public static String likeVideoLock(String userId, String videoId) {
        return LOCK_PREFIX + "like:video:" + userId + ":" + videoId;
    }

    public static String likeCommentLock(String userId, String commentId) {
        return LOCK_PREFIX + "like:comment:" + userId + ":" + commentId;
    }

    public static String followLock(String fromUserId, String toUserId) {
        return LOCK_PREFIX + "relation:follow:" + fromUserId + ":" + toUserId;
    }

    public static String commentDeleteLock(String commentId) {
        return LOCK_PREFIX + "comment:delete:" + commentId;
    }

    public static String contactList(String userId, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "contact:list:" + userId + ":" + pageNum + ":" + pageSize;
    }

    public static String contactListPrefix(String userId) {
        return CACHE_PREFIX + "contact:list:" + userId + ":";
    }

    public static String sessionList(String userId, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "chat:session:list:" + userId + ":" + pageNum + ":" + pageSize;
    }

    public static String sessionListPrefix(String userId) {
        return CACHE_PREFIX + "chat:session:list:" + userId + ":";
    }

    public static String messageHistory(String conversationId, String startTime, String endTime, Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "chat:history:" + conversationId + ":" + startTime + ":" + endTime + ":" + pageNum + ":" + pageSize;
    }

    public static String messageHistoryPrefix(String conversationId) {
        return CACHE_PREFIX + "chat:history:" + conversationId + ":";
    }

    public static String messageRecentList(String conversationId) {
        return CACHE_PREFIX + "chat:recent:" + conversationId;
    }

    public static String conversationOnlineUser(String userId) {
        return CACHE_PREFIX + "chat:online:user:" + userId;
    }

    public static String videoAuditPending(Integer pageNum, Integer pageSize) {
        return CACHE_PREFIX + "video:audit:pending:" + pageNum + ":" + pageSize;
    }

    public static String videoAuditPendingPrefix() {
        return CACHE_PREFIX + "video:audit:pending:";
    }

    public static String videoAuditLock(String videoId) {
        return LOCK_PREFIX + "video:audit:" + videoId;
    }

    public static String sessionCreateLock(String userId, String bizKey) {
        return LOCK_PREFIX + "chat:session:create:" + userId + ":" + bizKey;
    }

    public static String messageSendLock(String userId, String conversationId) {
        return LOCK_PREFIX + "chat:message:send:" + userId + ":" + conversationId;
    }

    public static String contactBlockLock(String userId, String targetUserId) {
        return LOCK_PREFIX + "contact:block:" + userId + ":" + targetUserId;
    }

    public static String strangerFirstMessage(String senderUserId, String targetUserId) {
        return "im:stranger:first-message:" + senderUserId + ":" + targetUserId;
    }
}
