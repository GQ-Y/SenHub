package com.digital.video.gateway.auth;

/**
 * 用于在 before 过滤器中终止请求并返回指定状态码和响应体（替代 Spark.halt）。
 */
public class HaltException extends RuntimeException {
    private final int status;
    private final String body;

    public HaltException(int status, String body) {
        super("Halt: " + status);
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}
