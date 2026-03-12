package com.danmaku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danmaku.entity.Like;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LikeMapper extends BaseMapper<Like> {
}