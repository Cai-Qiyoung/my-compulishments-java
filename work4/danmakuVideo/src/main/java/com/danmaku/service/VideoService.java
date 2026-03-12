package com.danmaku.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danmaku.entity.Video;
import com.danmaku.vo.ResultVo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface VideoService extends IService<Video> {
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

}