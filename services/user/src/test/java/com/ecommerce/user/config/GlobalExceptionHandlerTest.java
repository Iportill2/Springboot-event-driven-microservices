package com.ecommerce.user.config;

import com.ecommerce.common.api.ApiResponse;
import com.ecommerce.user.controller.AuthController;
import com.ecommerce.user.controller.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentBecomesBadRequestWithTheOriginalMessage() {
        ApiResponse<Void> response = handler.badRequest(new IllegalArgumentException("Username is already taken"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Username is already taken");
    }

    @Test
    void constraintViolationBecomesConflict() {
        // Registros concurrentes con el mismo username pasan ambos el existsBy y uno
        // revienta contra uk_users_username.
        ApiResponse<Void> response = handler.conflict(new DataIntegrityViolationException("uk_users_username"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Resource already exists");
    }

    @Test
    void validationFailureListsTheOffendingFields() throws Exception {
        Method method = AuthController.class.getDeclaredMethod("login", LoginRequest.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "loginRequest");
        bindingResult.addError(new FieldError("loginRequest", "username", "must not be blank"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ApiResponse<Void> response = handler.validation(ex);

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("username").contains("must not be blank");
    }

    @Test
    void unexpectedFailureBecomesInternalServerErrorWithoutLeakingInternals() {
        ApiResponse<Void> response =
                handler.generic(new IllegalStateException("jdbc:postgresql://user_db unreachable"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Internal server error");
    }

    @Test
    void everyHandlerDeclaresItsResponseStatus() throws Exception {
        assertThat(statusOf("badRequest", IllegalArgumentException.class)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf("conflict", DataIntegrityViolationException.class)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf("validation", MethodArgumentNotValidException.class)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf("generic", Exception.class)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static HttpStatus statusOf(String methodName, Class<?> parameterType) throws Exception {
        ResponseStatus annotation = GlobalExceptionHandler.class
                .getDeclaredMethod(methodName, parameterType)
                .getAnnotation(ResponseStatus.class);
        assertThat(annotation).as("@ResponseStatus on " + methodName).isNotNull();
        return annotation.value();
    }
}
