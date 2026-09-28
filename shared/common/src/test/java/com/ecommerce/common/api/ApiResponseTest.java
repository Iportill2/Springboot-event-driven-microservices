package com.ecommerce.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void okWithDataIsSuccessfulAndCarriesThePayload() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void okWithMessageKeepsBothMessageAndPayload() {
        ApiResponse<String> response = ApiResponse.ok("Product created", "payload");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Product created");
        assertThat(response.data()).isEqualTo("payload");
    }

    @Test
    void errorIsNotSuccessfulAndHasNoData() {
        ApiResponse<Void> response = ApiResponse.error("SKU already exists: SKU-1");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("SKU already exists: SKU-1");
        assertThat(response.data()).isNull();
    }

    @Test
    void everyResponseCarriesATimestamp() {
        assertThat(ApiResponse.ok("x").timestamp()).isNotNull();
        assertThat(ApiResponse.error("x").timestamp()).isNotNull();
    }
}
