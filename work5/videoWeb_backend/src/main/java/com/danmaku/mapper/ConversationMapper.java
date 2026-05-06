package com.danmaku.mapper;

import com.danmaku.entity.Conversation;
import com.danmaku.vo.SessionItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConversationMapper {
    Conversation selectById(@Param("id") String id);

    Conversation selectByBizKey(@Param("bizKey") String bizKey);

    int insert(Conversation conversation);

    int updateLastMessage(@Param("conversationId") String conversationId,
                          @Param("lastMessage") String lastMessage,
                          @Param("lastMessageType") String lastMessageType,
                          @Param("lastMessageTime") LocalDateTime lastMessageTime);

    long countUserSessions(@Param("userId") String userId);

    List<SessionItemVO> selectUserSessions(@Param("userId") String userId,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);
}
