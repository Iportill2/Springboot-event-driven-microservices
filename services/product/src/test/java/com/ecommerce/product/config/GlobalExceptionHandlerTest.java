package com.ecommerce.product.config;

import com.ecommerce.common.api.ApiResponse;
import com.ecommerce.product.controller.ProductController;
import com.ecommerce.product.controller.ProductRequest;
import com.ecommerce.product.service.ProductNotFoundException;
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
        ApiResponse<Void> response = handler.badRequest(new IllegalArgumentException("SKU already exists: SKU-1"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("SKU already exists: SKU-1");
    }

    @Test
    void missingProductBecomesNotFound() {
        ApiResponse<Void> response = handler.notFound(new ProductNotFoundException(99L));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("99");
    }

    @Test
    void constraintViolationBecomesConflict() {
        ApiResponse<Void> response = handler.conflict(new DataIntegrityViolationException("uk_products_sku"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Resource already exists");
    }

    @Test
    void validationFailureListsTheOffendingFields() throws Exception {
        Method method = ProductController.class.getDeclaredMethod("create", ProductRequest.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "product");
        bindingResult.addError(new FieldError("product", "price", "must be greater than or equal to 0.01"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ApiResponse<Void> response = handler.validation(ex);

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("price").contains("0.01");
    }

    @Test
    void unexpectedFailureBecomesInternalServerErrorWithoutLeakingInternals() {
        ApiResponse<Void> response =
                handler.generic(new IllegalStateException("connection pool exhausted at com.zaxxer.hikari..."));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Internal server error");
    }

    @Test
    void everyHandlerDeclaresItsResponseStatus() throws Exception {
        // Sin @ResponseStatus el handler devolveria 200 con success=false, que es
        // justo el fallo que cuesta de detectar. Fijamos el codigo declarado.
        assertThat(statusOf("badRequest", IllegalArgumentException.class)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf("notFound", ProductNotFoundException.class)).isEqualTo(HttpStatus.NOT_FOUND);
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
