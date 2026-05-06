package com.danmaku.mapper;

import com.danmaku.entity.Like;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LikeMapper {
    Like selectVideoLike(@Param("userId") String userId,
                         @Param("videoId") String videoId,
                         @Param("type") int type);

    Like selectCommentLike(@Param("userId") String userId,
                           @Param("commentId") String commentId,
                           @Param("type") int type);

    long countVideoLikeByUser(@Param("userId") String userId,
                              @Param("videoId") String videoId,
                              @Param("type") int type);

    long countByUserId(@Param("userId") String userId);

    List<Like> selectByUserPageWithVideo(@Param("userId") String userId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    int insert(Like like);

    int deleteById(@Param("id") String id);
}
