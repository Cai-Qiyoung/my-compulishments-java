package com.danmaku.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("relation")
public class Relation {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String fromUserId; // 关注者id
    private String toUserId; // 被关注者id
    private Integer status = 0;// 关系状态 0：关注 1:未关注

}