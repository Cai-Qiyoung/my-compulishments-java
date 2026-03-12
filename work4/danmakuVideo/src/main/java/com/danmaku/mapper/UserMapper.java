package com.danmaku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danmaku.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}