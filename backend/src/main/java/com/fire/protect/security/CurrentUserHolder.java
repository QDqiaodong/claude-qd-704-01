package com.fire.protect.security;

import com.fire.protect.common.ForbiddenException;
import com.fire.protect.domain.SysUser;

/**
 * 当前操作人上下文。由 {@link UserHeaderFilter} 在每个请求开始时按 {@code X-User-Id}
 * 头解析并写入，请求结束清理。服务层据此做角色校验，所有关键动作的留痕也取这里的操作者。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<SysUser> CURRENT = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(SysUser user) {
        CURRENT.set(user);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 未登录（前端没带头）直接 403，避免把动作记到空操作者头上。 */
    public static SysUser require() {
        SysUser user = CURRENT.get();
        if (user == null) {
            throw new ForbiddenException("未识别到当前操作人，请在顶栏选择身份后再操作");
        }
        return user;
    }

    public static boolean isRole(String role) {
        SysUser user = CURRENT.get();
        return user != null && role.equals(user.getRole());
    }

    /** 角色不满足时抛 403，并带上需要的角色中文名。 */
    public static void requireRole(String role, String roleName) {
        SysUser user = require();
        if (!role.equals(user.getRole())) {
            throw new ForbiddenException("只有" + roleName + "可以执行该操作，当前身份：" + user.getDisplayName());
        }
    }
}
