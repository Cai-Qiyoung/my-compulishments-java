package com.danmaku.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("like_record")
public class Like {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId; // 用户id
    private String videoId; // 视频id
    private String commentId; // 评论id
    private Integer type; // 点赞对象 1:视频 2:评论

    @TableField(exist = false)
    private Video video;

    public void setVideo(Video video) {
        this.video = video;
    }
}