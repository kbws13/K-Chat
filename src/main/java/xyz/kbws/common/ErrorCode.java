package xyz.kbws.common;

/**
 * 自定义错误码
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    TOKEN_ERROR(50100, "Token解析失败"),
    NON_FRIEND(902, "您不是对方的好友，请先向好友发送朋友验证申请"),
    NOT_IN_GROUP_CHAT(903, "你已经不在群聊，请重新加入群聊"),
    FILE_DOES_NOT_EXIST(903, "文件不存在"),
    OPERATION_ERROR(50001, "操作失败");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
