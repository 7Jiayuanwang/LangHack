package com.english.exception;

import com.english.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 GlobalExceptionHandler 为各类异常返回正确的 HTTP 状态码和消息。
 */
public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    public void businessException_shouldReturn400() {
        Result<?> result = handler.handleBusinessException(new BusinessException("用户不存在"));

        assertEquals(400, result.getCode(), "BusinessException 应返回 400");
        assertEquals("用户不存在", result.getMessage());
    }

    @Test
    public void businessException_withNullMessage_shouldReturn400WithDefaultMsg() {
        Result<?> result = handler.handleBusinessException(new BusinessException((String) null));

        assertEquals(400, result.getCode());
        assertNotNull(result.getMessage());
    }

    @Test
    public void illegalArgument_shouldReturn400() {
        Result<?> result = handler.handleIllegalArgument(new IllegalArgumentException("参数非法: userId 不能为空"));

        assertEquals(400, result.getCode(), "IllegalArgumentException 应返回 400");
        assertEquals("参数非法: userId 不能为空", result.getMessage());
    }

    @Test
    public void illegalArgument_withNullMessage_shouldReturn400WithDefaultMsg() {
        Result<?> result = handler.handleIllegalArgument(new IllegalArgumentException((String) null));

        assertEquals(400, result.getCode());
        assertNotNull(result.getMessage());
    }

    @Test
    public void runtimeException_shouldReturn500() {
        Result<?> result = handler.handleRuntimeException(new RuntimeException("NullPointerException in service"));

        assertEquals(500, result.getCode(), "RuntimeException 应返回 500");
        assertNotNull(result.getMessage());
    }

    @Test
    public void genericException_shouldReturn500() {
        Result<?> result = handler.handleException(new Exception("Unexpected IO error"));

        assertEquals(500, result.getCode(), "未知 Exception 应返回 500");
        assertNotNull(result.getMessage());
    }

    @Test
    public void methodArgumentNotValid_shouldReturn400WithFieldErrors() throws NoSuchMethodException {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("user", "email", "邮箱格式不正确");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        // Construct a real MethodParameter to avoid mocking final class
        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("methodArgumentNotValid_shouldReturn400WithFieldErrors"), -1);

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        Result<?> result = handler.handleValidation(ex);

        assertEquals(400, result.getCode(), "参数校验失败应返回 400");
        assertTrue(result.getMessage().contains("邮箱格式不正确"),
                "错误消息应包含字段错误详情");
    }

    @Test
    public void httpMessageNotReadable_shouldReturn400() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("JSON parse error");
        when(ex.getCause()).thenReturn(null);

        Result<?> result = handler.handleMessageNotReadable(ex);

        assertEquals(400, result.getCode(), "请求体不可读应返回 400");
        assertNotNull(result.getMessage());
    }

    @Test
    public void runtimeException_shouldNotExposeInternalDetail() {
        String internalDetail = "SELECT * FROM users WHERE id=1 -- internal query";
        Result<?> result = handler.handleRuntimeException(new RuntimeException(internalDetail));

        assertFalse(result.getMessage().contains(internalDetail),
                "生产环境不应将内部异常细节暴露给客户端");
    }
}
