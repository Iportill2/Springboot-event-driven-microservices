package com.ecommerce.product.service;

import com.ecommerce.common.events.ProductCreatedEvent;
import com.ecommerce.product.controller.ProductRequest;
import com.ecommerce.product.controller.ProductResponse;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ProductEventPublisher eventPublisher;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        productService = new ProductService(repository, redisTemplate, objectMapper, eventPublisher);
    }

    private static Product persistedProduct(Long id, String name, String sku) {
        Product product = new Product(name, sku, "desc", new BigDecimal("999.99"));
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(product, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
        return product;
    }

    private static ProductRequest request(String name, String sku) {
        return new ProductRequest(name, sku, "desc", new BigDecimal("999.99"));
    }

    private void givenCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    // ---------- create ----------

    @Test
    void createSavesCachesAndReturnsTheProduct() {
        Product saved = persistedProduct(1L, "Laptop", "SKU-1");
        when(repository.existsBySku("SKU-1")).thenReturn(false);
        when(repository.save(any(Product.class))).thenReturn(saved);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ProductResponse response = productService.create(request("Laptop", "SKU-1"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sku()).isEqualTo("SKU-1");
        verify(valueOperations).set(eq("product:1"), anyString(), any());
    }

    @Test
    void createPublishesExactlyOneProductCreatedEvent() {
        when(repository.existsBySku("SKU-1")).thenReturn(false);
        when(repository.save(any(Product.class))).thenReturn(persistedProduct(1L, "Laptop", "SKU-1"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        productService.create(request("Laptop", "SKU-1"));

        ArgumentCaptor<ProductCreatedEvent> captor = ArgumentCaptor.forClass(ProductCreatedEvent.class);
        verify(eventPublisher, times(1)).productCreated(captor.capture());
        assertThat(captor.getValue().productId()).isEqualTo(1L);
        assertThat(captor.getValue().sku()).isEqualTo("SKU-1");
    }

    @Test
    void createRejectsADuplicateSkuWithoutTouchingTheDatabase() {
        when(repository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request("Laptop", "SKU-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU-1");

        verify(repository, never()).save(any(Product.class));
        verifyNoInteractions(eventPublisher);
    }

    // ---------- update ----------

    @Test
    void updateRejectsASkuOwnedByAnotherProduct() {
        // uk_products_sku es unico en BD; sin el pre-check el UPDATE reventaba con
        // DataIntegrityViolationException y el cliente recibia un 500.
        when(repository.findById(1L)).thenReturn(Optional.of(persistedProduct(1L, "Old", "SKU-OLD")));
        when(repository.existsBySkuAndIdNot("SKU-2", 1L)).thenReturn(true);

        assertThatThrownBy(() -> productService.update(1L, request("Laptop", "SKU-2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU-2");

        verify(repository, never()).save(any(Product.class));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void updateAllowsKeepingTheSameSku() {
        // Guarda contra un futuro existsBySku mal implementado que excluyera de mas
        // y dejara al producto sin poder reescribir su propio SKU.
        when(repository.findById(1L)).thenReturn(Optional.of(persistedProduct(1L, "Old", "SKU-1")));
        when(repository.existsBySkuAndIdNot("SKU-1", 1L)).thenReturn(false);
        when(repository.save(any(Product.class))).thenReturn(persistedProduct(1L, "New", "SKU-1"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ProductResponse response = productService.update(1L, request("New", "SKU-1"));

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.sku()).isEqualTo("SKU-1");
    }

    @Test
    void updateOnAMissingProductRaisesNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, request("Laptop", "SKU-1")))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(repository, never()).save(any(Product.class));
    }

    // ---------- delete ----------

    @Test
    void deleteOnAMissingProductRaisesNotFoundInsteadOfSilentlySucceeding() {
        // JpaRepository.deleteById es un no-op silencioso si el id no existe, asi que
        // sin la comprobacion explicita un DELETE de un id inexistente daria 200.
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(repository, never()).deleteById(any());
    }

    @Test
    void deleteRemovesTheRowAndEvictsTheCacheEntry() {
        when(repository.existsById(1L)).thenReturn(true);

        productService.delete(1L);

        verify(repository).deleteById(1L);
        verify(redisTemplate).delete("product:1");
    }

    // ---------- findById / cache-aside ----------

    @Test
    void findByIdServesFromCacheWithoutQueryingTheDatabase() {
        String cached = "{\"id\":1,\"name\":\"Laptop\",\"sku\":\"SKU-1\",\"description\":\"desc\","
                + "\"price\":999.99,\"active\":true,\"createdAt\":null,\"updatedAt\":null}";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:1")).thenReturn(cached);

        ProductResponse response = productService.findById(1L);

        assertThat(response.name()).isEqualTo("Laptop");
        verifyNoInteractions(repository);
    }

    @Test
    void findByIdFallsBackToTheDatabaseAndRepopulatesTheCache() {
        givenCacheMiss();
        when(repository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(persistedProduct(1L, "Laptop", "SKU-1")));

        ProductResponse response = productService.findById(1L);

        assertThat(response.sku()).isEqualTo("SKU-1");
        verify(valueOperations).set(eq("product:1"), anyString(), any());
    }

    @Test
    void findByIdEvictsACorruptCacheEntryAndFallsBackToTheDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:1")).thenReturn("{{{ not json");
        when(repository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(persistedProduct(1L, "Laptop", "SKU-1")));

        ProductResponse response = productService.findById(1L);

        assertThat(response.sku()).isEqualTo("SKU-1");
        verify(redisTemplate).delete("product:1");
    }

    @Test
    void findByIdOnAMissingOrInactiveProductRaisesNotFound() {
        givenCacheMiss();
        when(repository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ---------- findAll ----------

    @Test
    void findAllReturnsEveryActiveProduct() {
        when(repository.findByActiveTrue()).thenReturn(List.of(
                persistedProduct(1L, "Laptop", "SKU-1"),
                persistedProduct(2L, "Phone", "SKU-2")));

        assertThat(productService.findAll()).hasSize(2);
        verify(repository).findByActiveTrue();
    }
}
