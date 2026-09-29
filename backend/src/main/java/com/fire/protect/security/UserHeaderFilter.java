package com.fire.protect.security;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fire.protect.domain.SysUser;
import com.fire.protect.repository.SysUserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 从请求头 {@code X-User-Id} 解析当前操作人并写入 {@link CurrentUserHolder}。
 *
 * <p>演示系统不做真正的登录鉴权：顶栏切换身份 → 前端每个请求带头；
 * 头缺失或指向不存在的用户时按「未登录」处理，写操作会在服务层被 403 拦下，
 * 查询类接口（值班页等）允许匿名访问。
 */
@Component
@Order(10)
public class UserHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-User-Id";

    private final SysUserRepository sysUserRepository;

    public UserHeaderFilter(SysUserRepository sysUserRepository) {
        this.sysUserRepository = sysUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getHeader(HEADER);
        if (raw != null && !raw.isBlank()) {
            try {
                Long userId = Long.valueOf(raw.trim());
                SysUser user = sysUserRepository.findById(userId).orElse(null);
                if (user != null) {
                    CurrentUserHolder.set(user);
                }
            } catch (NumberFormatException ignored) {
                // 非法头值按未登录处理，由服务层统一 403
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUserHolder.clear();
        }
    }
}
