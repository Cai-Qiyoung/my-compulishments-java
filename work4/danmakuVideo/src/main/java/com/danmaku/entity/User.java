package com.danmaku.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private String id; // 雪花算法id

    private String username; // 用户名
    private String password; // 密码
    private String avatarUrl;// 头像链接

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;  // 创建时间

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;  // 更新时间

    private LocalDateTime deletedAt;  // 创建时间
}