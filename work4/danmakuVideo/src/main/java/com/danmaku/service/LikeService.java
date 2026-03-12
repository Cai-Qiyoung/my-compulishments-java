package com.danmaku.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danmaku.entity.Like;
import com.danmaku.vo.ResultVo;

public interface LikeService extends IService<Like> {
    /**
     * 点赞视频
     * @param accessToken
     * @param videoId
     * @return
     */
    ResultVo<?> likeVideo(String accessToken, String videoId);

    /**
     * 点赞评论
     * @param accessToken
     * @param commentId
     * @return
     */
    ResultVo<?> likeComment(String accessToken, String commentId);

    /**
     * 喜欢列表
     *
     * @param accessToken
     * @param user_id
     * @param page_num
     * @param page_size
     * @return
     */
    ResultVo<?> likeList(String accessToken,String user_id,Integer page_num,Integer page_size);
}