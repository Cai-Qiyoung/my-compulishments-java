package com.danmaku.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danmaku.entity.User;
import com.danmaku.mapper.UserMapper;
import com.danmaku.service.UserService;
import com.danmaku.util.FileUploadUtil;
import com.danmaku.util.JwtUtil;
import com.danmaku.vo.ResultVo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private FileUploadUtil fileUploadUtil;

    @Override
    public ResultVo<?> register(String username, String password) {
        // 校验用户名是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User existUser = userMapper.selectOne(wrapper);
        if (existUser != null) {
            return ResultVo.fail("用户名已存在");
        }
        // 密码加密
        String encodePwd = passwordEncoder.encode(password);
        // 创建用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodePwd);
        // 默认头像
        user.setAvatarUrl("http://localhost:10001/upload/avatar/default.jpg");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return ResultVo.success();
    }

    @Override
    public ResultVo<Map<String, Object>> login(String username, String password) {
        try {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, username);
            User user = userMapper.selectOne(wrapper);

            if (user == null) {
                return ResultVo.fail("用户不存在");
            }

            if (!passwordEncoder.matches(password, user.getPassword())) {
                return ResultVo.fail("密码错误");
            }

            // ============== 生成双令牌 ==============
            String accessToken = jwtUtil.generateAccessToken(Long.valueOf(user.getId()));
            String refreshToken = jwtUtil.generateRefreshToken(Long.valueOf(user.getId()));

            Map<String, Object> data = new HashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            data.put("avatar_url", user.getAvatarUrl());
            data.put("access_token",accessToken);    // 前端放header
            data.put("refresh_token", refreshToken);  // 用来刷新token

            return ResultVo.success(data);

        } catch (Exception e) {
            e.printStackTrace();
            return ResultVo.fail("登录异常：" + e.getMessage());
        }
    }

    @Override
    public ResultVo<Map<String, Object>> getUserInfo(String accessToken) {
        Long userId = jwtUtil.getUserIdFromToken(accessToken);
        User user = userMapper.selectById(String.valueOf(userId));
        if (user == null) {
            return ResultVo.fail("用户不存在");
        }
        // 隐藏密码
        user.setPassword(null);
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("avatar_url", user.getAvatarUrl());
        data.put("created_at", user.getCreatedAt());
        data.put("updated_at", user.getUpdatedAt());
        data.put("deleted_at", user.getDeletedAt());

        return ResultVo.success(data);
    }

    @Override
    public ResultVo<Map<String, Object>> uploadAvatar(String accessToken, MultipartFile file) {
        Long userId = jwtUtil.getUserIdFromToken(accessToken);

        // 校验文件
        if (file.isEmpty()) {
            return ResultVo.fail("请选择头像文件");
        }
        // 上传头像
        String avatarUrl = fileUploadUtil.uploadAvatar(file);
        // 更新用户信息
        User user = new User();
        user.setId(String.valueOf(userId));
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("password", user.getPassword());
        data.put("avatar_url", user.getAvatarUrl());
        data.put("created_at", user.getCreatedAt());
        data.put("updated_at", user.getUpdatedAt());
        data.put("deleted_at", user.getDeletedAt());
        return ResultVo.success(data);
    }
}