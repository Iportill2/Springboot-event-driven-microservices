package com.ecommerce.product.service;

import com.ecommerce.common.events.ProductCreatedEvent;
import com.ecommerce.product.controller.ProductRequest;
import com.ecommerce.product.controller.ProductResponse;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final String CACHE_PREFIX = "product:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ProductRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductEventPublisher eventPublisher;

    public ProductService(ProductRepository repository, StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper, ProductEventPublisher eventPublisher) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return repository.findByActiveTrue().stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        String cacheKey = cacheKey(id);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            ProductResponse fromCache = deserialize(cached);
            if (fromCache != null) {
                log.debug("Cache HIT for {}", cacheKey);
                return fromCache;
            }
            log.warn("Invalid cache entry for {}, evicting", cacheKey);
            redisTemplate.delete(cacheKey);
        }

        log.debug("Cache MISS for {}, loading from PostgreSQL", cacheKey);
        Product product = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        redisTemplate.opsForValue().set(cacheKey, serialize(product), CACHE_TTL);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (repository.existsBySku(request.sku())) {
            throw new IllegalArgumentException("SKU already exists: " + request.sku());
        }
        Product product = new Product(request.name(), request.sku(), request.description(), request.price());
        product = repository.save(product);
        cachePut(product);
        eventPublisher.productCreated(ProductCreatedEvent.of(product.getId(), product.getName(), product.getSku()));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product = repository.save(product);
        cachePut(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
        redisTemplate.delete(cacheKey(id));
    }

    private void cachePut(Product product) {
        redisTemplate.opsForValue().set(cacheKey(product.getId()), serialize(product), CACHE_TTL);
    }

    private String cacheKey(Long id) {
        return CACHE_PREFIX + id;
    }

    private String serialize(Product product) {
        try {
            return objectMapper.writeValueAsString(product);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize product", e);
        }
    }

    private ProductResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProductResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Invalid cache entry, evicting: {}", json);
            return null;
        }
    }
}