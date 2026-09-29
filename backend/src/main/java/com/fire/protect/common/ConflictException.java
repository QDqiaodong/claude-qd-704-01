package com.fire.protect.common;

/**
 * 乐观锁冲突：同一版本只允许一个动作成功（并发批准 / 撤销 / 复位 / 关闭）。
 * HTTP 409，调用方应重新拉取详情后重试。
 */
public class ConflictException extends BizException {

    public ConflictException(String message) {
        super(message);
    }
}
