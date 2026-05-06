package com.danmaku.service;

import com.danmaku.vo.ResultVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface VideoService {
    /**
     * 异步投稿视频
     * @param accessToken token
     * @param videoFile 视频文件
     * @param coverFile 视频文件
     * @param title 视频标题
     * @param description 视频描述
     * @return 投稿结果
     */
    ResultVo<?> publishVideo(String accessToken, MultipartFile videoFile, MultipartFile coverFile, String title, String description);

    /**
     * 获取用户发布的视频列表
     * @param accessToken token
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 视频列表
     */
    ResultVo<Map<String, Object>> getVideoList(String accessToken,String user_id , Integer pageNum, Integer pageSize);

    /**
     * 获取热门视频排行榜(Redis缓存)
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 热门视频列表
     */
    ResultVo<Map<String, Object>> getPopularVideo(Integer pageNum, Integer pageSize);

    /**
     * 搜索视频
     * @param keywords 搜索参数
     * @return 搜索结果
     */
    ResultVo<?> searchVideo(String keywords, Integer pageNum, Integer pageSize);

    /**
     * 获取视频详细信息
     * @param videoId 视频ID
     * @param accessToken 可选，用于判断当前用户是否点赞该视频
     * @return 视频完整信息（含发布者、点赞/评论/播放量、是否点赞等）
     */
    ResultVo<Map<String, Object>> getVideoDetail(String videoId, String accessToken);

    ResultVo<?> getPendingAuditVideos(Integer pageNum, Integer pageSize);

    ResultVo<?> reviewVideo(String accessToken, String videoId, String auditStatus, String auditReason);

}
