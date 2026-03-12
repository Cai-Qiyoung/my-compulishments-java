package com.danmaku.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("comment") // 对应数据库表名：comment
public class Comment {
    @TableId(type = IdType.ASSIGN_ID) // 主键，使用雪花算法自动生成ID
    private String id;

    private String userId; // 用户ID
    private String videoId; // 视频ID
    private String parentId = "0"; // 父评论ID，0表示根评论
    private Integer likeCount = 0; // 点赞数，默认0
    private Integer childCount = 0; // 子评论数，默认0
    private String content; // 评论内容

    @TableField(fill = FieldFill.INSERT) // 插入时自动填充
    private LocalDateTime createdAt; // 创建时间

    @TableField(fill = FieldFill.INSERT_UPDATE) // 插入和更新时自动填充
    private LocalDateTime updatedAt; // 更新时间

    private LocalDateTime deletedAt; // 删除时间（逻辑删除专用）
}