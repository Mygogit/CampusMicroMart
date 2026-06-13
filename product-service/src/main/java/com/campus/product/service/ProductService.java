package com.campus.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.product.dto.AuditProductDTO;
import com.campus.product.dto.CreateProductDTO;
import com.campus.product.entity.Product;

public interface ProductService extends IService<Product> {
    Product createProduct(CreateProductDTO dto);
    boolean deductStock(Long productId, Integer quantity);
    boolean rollbackStock(Long productId, Integer quantity);
    void auditProduct(AuditProductDTO dto);
    void offShelf(Long productId);
    void cancelProduct(Long productId);
    void markSold(Long productId);
}
