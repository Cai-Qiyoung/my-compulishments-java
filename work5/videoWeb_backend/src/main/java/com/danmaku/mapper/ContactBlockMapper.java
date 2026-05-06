package com.danmaku.mapper;

import com.danmaku.entity.ContactBlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ContactBlockMapper {
    ContactBlock selectByUsers(@Param("blockerUserId") String blockerUserId,
                               @Param("blockedUserId") String blockedUserId);

    int insert(ContactBlock contactBlock);

    int updateStatusById(@Param("id") String id, @Param("status") Integer status);

    long countActiveBetween(@Param("leftUserId") String leftUserId,
                            @Param("rightUserId") String rightUserId);

    List<ContactBlock> selectActiveBlocks(@Param("userIds") Collection<String> userIds);
}
