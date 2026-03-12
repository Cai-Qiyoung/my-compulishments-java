package com.danmaku.controller;

import com.danmaku.service.UserService;
import com.danmaku.util.JwtUtil;
import com.danmaku.vo.ResultVo;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;
    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/refreshToken")
    public ResultVo<?> refreshToken(
            @RequestHeader("Refresh-Token") String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            return ResultVo.fail("refreshToken无效");
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        String newAccessToken = jwtUtil.generateAccessToken(userId);

        Map<String, String> data = new HashMap<>();
        data.put("accessToken", newAccessToken);
        return ResultVo.success(data);
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResultVo<?> register(@RequestParam String username, @RequestParam String password) {
        return userService.register(username, password);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResultVo<?> login(@RequestParam String username, @RequestParam String password) {
        return userService.login(username, password);
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    public ResultVo<?> getUserInfo(@RequestHeader("Access-Token") String accessToken) {
        return userService.getUserInfo(accessToken);
    }

    /**
     * 上传头像
     */
    @PutMapping("/avatar/upload")
    public ResultVo<?> uploadAvatar(
            @RequestHeader("Access-Token") String accessToken,
            @RequestParam MultipartFile data) {
        return userService.uploadAvatar(accessToken, data);
    }
}