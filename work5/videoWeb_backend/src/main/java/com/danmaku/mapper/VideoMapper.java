package com.danmaku.mapper;

import com.danmaku.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface VideoMapper {
    int insert(Video video);

    Video selectById(@Param("id") String id);

    Video selectAdminById(@Param("id") String id);

    List<Video> selectByIds(@Param("ids") Collection<String> ids);

    long countByUserId(@Param("userId") String userId,
                       @Param("includePending") boolean includePending);

    List<Video> selectByUserIdPage(@Param("userId") String userId,
                                   @Param("includePending") boolean includePending,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countPendingAuditVideos();

    List<Video> selectPendingAuditVideos(@Param("offset") int offset,
                                         @Param("limit") int limit);

    int reviewVideo(@Param("videoId") String videoId,
                    @Param("auditStatus") String auditStatus,
                    @Param("auditReason") String auditReason,
                    @Param("auditBy") String auditBy);

    long countSearchVideos(@Param("keywords") String keywords,
                           @Param("matchedUserIds") List<String> matchedUserIds,
                           @Param("auditStatus") String auditStatus);

    List<Video> searchVideos(@Param("keywords") String keywords,
                             @Param("matchedUserIds") List<String> matchedUserIds,
                             @Param("auditStatus") String auditStatus,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    int incrementVisitCount(@Param("videoId") String videoId, @Param("delta") int delta);

    int incrementLikeCount(@Param("videoId") String videoId, @Param("delta") int delta);

    int adjustLikeCountSafely(@Param("videoId") String videoId, @Param("delta") int delta);

    int incrementCommentCount(@Param("videoId") String videoId, @Param("delta") long delta);

    int adjustCommentCountSafely(@Param("videoId") String videoId, @Param("delta") long delta);
}
