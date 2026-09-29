package com.fire.protect.common;

/**
 * 无权限操作：403。与 {@link BizException}（业务校验，400）区分开，
 * 便于验收时识别「模拟无权限操作」。
 */
public class ForbiddenException extends BizException {

    public ForbiddenException(String message) {
        super(message);
    }
}
