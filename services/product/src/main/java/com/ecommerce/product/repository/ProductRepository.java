package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    /**
     * Para validar un UPDATE hay que excluir al propio producto de la comprobacion,
     * si no un producto no podria renombrarse ni reescribir su propio SKU.
     */
    boolean existsBySkuAndIdNot(String sku, Long id);

    List<Product> findByActiveTrue();

    Optional<Product> findByIdAndActiveTrue(Long id);
}