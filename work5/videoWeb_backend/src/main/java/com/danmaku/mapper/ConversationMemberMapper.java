package com.danmaku.mapper;

import com.danmaku.entity.ConversationMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationMemberMapper {
    int insertBatch(@Param("members") List<ConversationMember> members);

    List<ConversationMember> selectByConversationId(@Param("conversationId") String conversationId);

    ConversationMember selectByConversationIdAndUserId(@Param("conversationId") String conversationId,
                                                        @Param("userId") String userId);

    List<String> selectUserIdsByConversationId(@Param("conversationId") String conversationId);

    List<String> selectSharedConversationIds(@Param("leftUserId") String leftUserId,
                                             @Param("rightUserId") String rightUserId);
}
