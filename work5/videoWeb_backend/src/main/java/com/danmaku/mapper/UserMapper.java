package com.danmaku.mapper;

import com.danmaku.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface UserMapper {
    User selectById(@Param("id") String id);

    User selectByUsername(@Param("username") String username);

    List<User> selectByIds(@Param("ids") Collection<String> ids);

    List<String> selectIdsByUsernameLike(@Param("keyword") String keyword);

    String selectRoleById(@Param("id") String id);

    int insert(User user);

    int updateAvatarById(@Param("id") String id,
                         @Param("avatarUrl") String avatarUrl,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
