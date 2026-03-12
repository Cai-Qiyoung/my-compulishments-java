package com.danmaku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danmaku.entity.Video;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {
    List<Video> selectBatchIds(List<Object> ids);
}