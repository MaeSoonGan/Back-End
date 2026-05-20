package com.mock.maesoongan.common;

import com.mock.maesoongan.admin.dashboard.AdminDashboardController;
import com.mock.maesoongan.admin.member.AdminMemberController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(assignableTypes = {AdminDashboardController.class, AdminMemberController.class})
public class DashboardExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
