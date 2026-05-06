package com.danmaku.config;

import com.danmaku.constant.UserRole;
import com.danmaku.mapper.UserMapper;
import com.danmaku.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 1. 获取请求头中的 AccessToken
        String accessToken = request.getHeader("Access-Token");
        String refreshToken = request.getHeader("Refresh-Token");

        try {
            // ====================== 情况1：有 AccessToken ======================
            if (accessToken != null && !accessToken.isEmpty()) {
                try {
                    // 尝试解析
                    Long userId = jwtUtil.getUserIdFromToken(accessToken);

                    // 解析成功 → 放入认证信息
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, resolveAuthorities(userId));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                } catch (Exception e) {
                    // ====================== AccessToken 过期！自动刷新 ======================
                    if (refreshToken != null && !refreshToken.isEmpty()) {
                        // 校验 RefreshToken 是否有效
                        if (jwtUtil.validateToken(refreshToken) && jwtUtil.isRefreshToken(refreshToken)) {
                            // 刷新成功 → 生成新 AccessToken
                            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
                            String newAccessToken = jwtUtil.generateAccessToken(userId);

                            //新 token 放入响应头，返回给前端
                            response.setHeader("Access-Token", newAccessToken);
                            response.setHeader("Access-Control-Expose-Headers", "Access-Token");

                            // 用新 token 继续认证
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(userId, null, resolveAuthorities(userId));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            }

        } catch (Exception e) {
            // 异常不阻断
        }

        // 放行
        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(Long userId) {
        String role = userMapper.selectRoleById(String.valueOf(userId));
        if (role == null || role.isBlank()) {
            role = UserRole.USER;
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
