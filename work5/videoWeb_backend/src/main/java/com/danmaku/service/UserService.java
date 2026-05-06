package com.danmaku.service;

import com.danmaku.vo.ResultVo;
import com.danmaku.vo.UserInfoVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface UserService {
    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @return 注册结果
     */
    ResultVo<?> register(String username, String password );

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果(含Token、用户信息)
     */
    ResultVo<Map<String, Object>> login(String username, String password);

    /**
     * 获取用户信息
     * @param accessToken token
     * @return 用户信息
     */
    ResultVo<UserInfoVO> getUserInfo(String accessToken);

    /**
     * 上传/修改头像
     * @param accessToken token
     * @param file 头像文件
     * @return 修改后的用户信息
     */
    ResultVo<UserInfoVO> uploadAvatar(String accessToken, MultipartFile file);
}
