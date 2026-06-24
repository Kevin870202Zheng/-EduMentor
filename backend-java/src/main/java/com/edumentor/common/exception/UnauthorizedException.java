package com.edumentor.common.exception;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(401, message);
    }

    public UnauthorizedException() {
        super(401, "未认证，请先登录");
    }
}
