package com.danmaku.mapper;

import com.danmaku.entity.ChatMessage;
import com.danmaku.vo.ChatMessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatMessageMapper {
    int insert(ChatMessage chatMessage);

    List<ChatMessageVO> selectHistory(@Param("conversationId") String conversationId,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    long countHistory(@Param("conversationId") String conversationId,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);
}
