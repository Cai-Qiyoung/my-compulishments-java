package com.danmaku.mapper;

import com.danmaku.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {
    Comment selectById(@Param("id") String id);

    int insert(Comment comment);

    long countRootComments(@Param("videoId") String videoId);

    List<Comment> selectRootCommentsPage(@Param("videoId") String videoId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    long countByParentId(@Param("parentId") String parentId);

    int deleteById(@Param("id") String id);

    int deleteByParentId(@Param("parentId") String parentId);

    int incrementLikeCount(@Param("commentId") String commentId, @Param("delta") int delta);

    int adjustLikeCountSafely(@Param("commentId") String commentId, @Param("delta") int delta);

    int incrementChildCount(@Param("commentId") String commentId, @Param("delta") int delta);

    int adjustChildCountSafely(@Param("commentId") String commentId, @Param("delta") int delta);
}
