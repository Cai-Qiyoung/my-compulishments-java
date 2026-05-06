package com.danmaku.mapper;

import com.danmaku.entity.Relation;
import com.danmaku.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RelationMapper {
    Relation selectByUsers(@Param("fromUserId") String fromUserId,
                           @Param("toUserId") String toUserId);

    int insert(Relation relation);

    int updateStatusById(@Param("id") String id, @Param("status") Integer status);

    long countFollowUsers(@Param("userId") String userId);

    List<User> selectFollowUsers(@Param("userId") String userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countFansUsers(@Param("userId") String userId);

    List<User> selectFansUsers(@Param("userId") String userId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    long countFriendUsers(@Param("userId") String userId);

    List<User> selectFriendUsers(@Param("userId") String userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countMutualFollow(@Param("leftUserId") String leftUserId,
                           @Param("rightUserId") String rightUserId);
}
