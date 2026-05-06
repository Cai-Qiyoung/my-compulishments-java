package com.danmaku.service;

import com.danmaku.vo.ResultVo;

public interface RelationService {
    /**
     * 关注/取关用户
     *
     * @param accessToken
     * @param toUserId    被关注者ID
     * @return 关注/取关结果
     */
    ResultVo<?> followUser(String accessToken, String toUserId );

    /**
     * 获取用户关注列表
     * @param userId 用户ID
     * @param accessToken
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 关注列表分页结果
     */
    ResultVo<?> getFollowList(String userId, String accessToken ,Integer pageNum, Integer pageSize);

    /**
     * 获取用户粉丝列表
     * @param accessToken token
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 粉丝列表分页结果
     */
    ResultVo<?> getFansList(String userId, String accessToken ,Integer pageNum, Integer pageSize);

    /**
     * 获取好友列表
     * @param accessToken
     * @param pageNum
     * @param pageSize
     * @return 好友列表分页结果
     */
    ResultVo<?> getFriendList( String accessToken ,Integer pageNum, Integer pageSize);
}
