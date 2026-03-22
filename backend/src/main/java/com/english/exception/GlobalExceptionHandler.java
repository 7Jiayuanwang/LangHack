package com.english.exception;

import com.english.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 捕获 DispatcherServlet 分发链中的异常。
 *
 * 注意: Spring Security 过滤器链（Filter 层）抛出的 AccessDeniedException /
 * AuthenticationException 不会到达此处；它们由 SecurityConfig 中注册的
 * SecurityExceptionHandler bean 处理。
 *
 * 异常处理优先级（按顺序）：
 * 1. 具体异常（MethodArgumentNotValidException, DuplicateKeyException 等）
 * 2. BusinessException — 业务异常，message 可安全展示给前端 (400)
 * 3. RuntimeException — 运行时异常，隐藏详情，返回通用消息 (500)
 * 4. Exception — 兜底异常处理 (500)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验失败 (使用 @Valid / @Validated 注解时)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult() != null && e.getBindingResult().getFieldErrors() != null
                ? e.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining("; "))
                : "参数格式错误";
        log.warn("Validation failed: {}", msg);
        // 只在日志中记录详细信息，不返回给前端（防止用户输入泄漏）
        return Result.error(400, "参数校验失败，请检查请求参数");
    }

    /**
     * 请求体不可读 (格式错误 / Content-Type 不匹配)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("Request body not readable: {}", e.getMessage());
        return Result.error(400, "请求格式错误，请检查请求体");
    }

    /**
     * 非法参数 (业务代码主动抛出)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return Result.error(400, "参数非法");
    }

    /**
     * 唯一键约束冲突 (需要在 DataIntegrityViolationException 之前处理)
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return Result.error(400, "操作冲突，请稍后重试");
    }

    /**
     * 数据完整性违反 (唯一约束冲突等)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : "";
        if (msg.contains("Duplicate entry")) {
            log.warn("唯一约束冲突: {}", msg);
            return Result.error(400, "操作冲突，请稍后重试");
        }
        log.error("数据完整性违反", e);
        return Result.error(400, "数据操作失败");
    }

    /**
     * 类型转换异常（通常来自 Map 强制转换或反序列化）
     */
    @ExceptionHandler(ClassCastException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleClassCast(ClassCastException e) {
        log.warn("Type casting error: {}", e.getMessage());
        return Result.error(400, "请求数据格式不匹配，请检查字段类型");
    }

    /**
     * 业务逻辑异常 — message 可安全展示给前端
     * IMPORTANT: 必须在 RuntimeException 之前处理，确保 BusinessException 优先匹配
     * （因为 BusinessException extends RuntimeException）
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        String message = e.getMessage() != null ? e.getMessage() : "业务处理失败";
        return Result.error(400, message);
    }

    /**
     * 其他运行时异常
     * 不对外暴露异常细节，仅记录日志。
     * OPS 角色用户可看到错误分类摘要。
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception: {}", e.getMessage(), e);
        Result<?> result = Result.error("服务异常，请稍后重试");
        if (isOpsUser()) {
            result.setDetail(classifyError(e));
        }
        return result;
    }

    /**
     * 兜底处理器
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("Unexpected exception: {}", e.getMessage(), e);
        Result<?> result = Result.error("系统错误，请联系管理员");
        if (isOpsUser()) {
            result.setDetail(classifyError(e));
        }
        return result;
    }

    /**
     * 检查当前用户是否具有 OPS 角色
     */
    private boolean isOpsUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return false;
            }
            return auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_OPS".equals(a.getAuthority()));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 错误分类：为 OPS 用户提供简洁的错误分类码（不暴露内部类名/方法名/行号）
     * 详细信息已通过 log.error() 记录到日志系统，OPS 人员应通过日志系统查看
     */
    private String classifyError(Exception e) {
        if (e instanceof DataAccessException) {
            return "DB_ERROR";
        }
        if (e instanceof ConnectException) {
            return "CONN_ERROR";
        }
        if (e instanceof TimeoutException) {
            return "TIMEOUT";
        }
        if (e instanceof NullPointerException) {
            return "NPE";
        }
        return "INTERNAL_ERROR";
    }
}
