package com.danmaku.service.impl;

import cn.hutool.core.util.IdUtil;
import com.danmaku.constant.RedisKeys;
import com.danmaku.constant.UserRole;
import com.danmaku.entity.User;
import com.danmaku.mapper.UserMapper;
import com.danmaku.service.UserService;
import com.danmaku.util.FileUploadUtil;
import com.danmaku.util.JwtUtil;
import com.danmaku.util.RedisUtil;
import com.danmaku.vo.ResultVo;
import com.danmaku.vo.UserInfoVO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    private static final long USER_INFO_CACHE_TTL_MINUTES = 15L;
    private static final long REGISTER_LOCK_TTL_SECONDS = 5L;

    @Resource
    private UserMapper userMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private FileUploadUtil fileUploadUtil;
    @Resource
    private RedisUtil redisUtil;
    @Value("${app.base-url:http://120.55.191.140:9090}")
    private String baseUrl;

    @Override
    public ResultVo<?> register(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        String lockKey = RedisKeys.registerLock(normalizedUsername);
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(lockKey, lockValue, REGISTER_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            return ResultVo.fail("注册请求过于频繁，请稍后重试");
        }

        try {
            User existUser = userMapper.selectByUsername(normalizedUsername);
            if (existUser != null) {
                return ResultVo.fail("用户名已存在");
            }

            String encodePwd = passwordEncoder.encode(password);
            User user = new User();
            user.setId(IdUtil.getSnowflakeNextIdStr());
            user.setUsername(normalizedUsername);
            user.setPassword(encodePwd);
            user.setAvatarUrl(normalizeBaseUrl() + "/upload/avatar/default.jpg");
            user.setRole(UserRole.USER);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
            return ResultVo.success();
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    @Override
    public ResultVo<Map<String, Object>> login(String username, String password) {
        try {
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResultVo.fail("用户不存在");
            }

            if (!passwordEncoder.matches(password, user.getPassword())) {
                return ResultVo.fail("密码错误");
            }

            String accessToken = jwtUtil.generateAccessToken(Long.valueOf(user.getId()));
            String refreshToken = jwtUtil.generateRefreshToken(Long.valueOf(user.getId()));

            Map<String, Object> data = new HashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            data.put("avatar_url", user.getAvatarUrl());
            data.put("role", user.getRole());
            data.put("access_token",accessToken);    // 前端放header
            data.put("refresh_token", refreshToken);  // 用来刷新token

            return ResultVo.success(data);

        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("登录异常：" + e.getMessage());
        }
    }

    @Override
    public ResultVo<UserInfoVO> getUserInfo(String accessToken) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String cacheKey = RedisKeys.userInfo(String.valueOf(userId));
        UserInfoVO cached = redisUtil.getCacheObjectSafely(cacheKey);
        if (cached != null) {
            return ResultVo.success(cached);
        }

        User user = userMapper.selectById(String.valueOf(userId));
        if (user == null) {
            return ResultVo.fail("用户不存在");
        }

        UserInfoVO userInfo = buildUserInfo(user);
        redisUtil.setCacheObjectSafely(cacheKey, userInfo, USER_INFO_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(userInfo);
    }

    @Override
    public ResultVo<UserInfoVO> uploadAvatar(String accessToken, MultipartFile file) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 校验文件
        if (file.isEmpty()) {
            return ResultVo.fail("请选择头像文件");
        }
        // 上传头像
        String avatarUrl = fileUploadUtil.uploadAvatar(file);
        // 更新用户信息
        userMapper.updateAvatarById(String.valueOf(userId), avatarUrl, LocalDateTime.now());

        User latestUser = userMapper.selectById(String.valueOf(userId));
        UserInfoVO userInfo = buildUserInfo(latestUser);
        redisUtil.setCacheObjectSafely(RedisKeys.userInfo(String.valueOf(userId)), userInfo, USER_INFO_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(userInfo);
    }

    private String normalizeBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private UserInfoVO buildUserInfo(User user) {
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        userInfo.setCreatedAt(user.getCreatedAt());
        userInfo.setUpdatedAt(user.getUpdatedAt());
        userInfo.setDeletedAt(user.getDeletedAt());
        return userInfo;
    }
}
