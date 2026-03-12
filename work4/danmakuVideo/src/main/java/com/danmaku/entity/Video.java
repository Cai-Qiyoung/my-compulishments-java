package com.danmaku.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video")
public class Video {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId; // 作者id
    private String videoUrl; // 视频链接
    private String coverUrl; // 视频封面链接
    private String title; // 视频标题
    private String description; // 视频描述
    private Integer visitCount = 0; // 访问量
    private Integer likeCount = 0; // 点赞数
    private Integer commentCount = 0; // 评论数

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt; // 发布时间

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt; // 更新时间

    private LocalDateTime deletedAt; // 删除时间
}