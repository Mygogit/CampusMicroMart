package com.campus.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.constant.AuditStatusConstant;
import com.campus.common.constant.ProductStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.mq.MqTopicConstant;
import com.campus.common.security.UserContext;
import com.campus.product.dto.AuditProductDTO;
import com.campus.product.dto.CreateProductDTO;
import com.campus.product.entity.Product;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Override
    @Transactional
    public Product createProduct(CreateProductDTO dto) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setStockThreshold(5);
        product.setProductStatus(ProductStatusConstant.PENDING);
        product.setAuditStatus(AuditStatusConstant.PENDING);
        product.setCategoryId(dto.getCategoryId());
        product.setUserId(userId);
        product.setImages(dto.getImages());
        product.setCourseCode(dto.getCourseCode());
        product.setDormitory(dto.getDormitory());
        save(product);
        log.info("商品创建成功, productId={}, userId={}, name={}, auditStatus=PENDING", product.getId(), userId, product.getName());
        return product;
    }

    @Override
    @Transactional
    public boolean deductStock(Long productId, Integer quantity) {
        Product product = getById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Product::getId, productId)
               .ge(Product::getStock, quantity)
               .setSql("stock = stock - {0}", quantity);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException("库存不足");
        }

        Product updated = getById(productId);
        if (updated.getStock() <= 0) {
            LambdaUpdateWrapper<Product> offWrapper = new LambdaUpdateWrapper<>();
            offWrapper.eq(Product::getId, productId)
                      .set(Product::getProductStatus, ProductStatusConstant.OFF_SHELF);
            baseMapper.update(null, offWrapper);
            log.info("商品库存为0, 自动下架, productId={}", productId);
        } else if (updated.getStock() <= updated.getStockThreshold()) {
            sendStockAlert(updated);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean rollbackStock(Long productId, Integer quantity) {
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Product::getId, productId)
               .setSql("stock = stock + {0}", quantity);
        int rows = baseMapper.update(null, wrapper);
        if (rows > 0) {
            Product updated = getById(productId);
            if (updated.getProductStatus().equals(ProductStatusConstant.OFF_SHELF) && updated.getStock() > 0) {
                LambdaUpdateWrapper<Product> onWrapper = new LambdaUpdateWrapper<>();
                onWrapper.eq(Product::getId, productId)
                         .set(Product::getProductStatus, ProductStatusConstant.ON_SALE);
                baseMapper.update(null, onWrapper);
            }
        }
        return rows > 0;
    }

    @Override
    @Transactional
    public void auditProduct(AuditProductDTO dto) {
        Product product = getById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (!product.getAuditStatus().equals(AuditStatusConstant.PENDING)) {
            throw new BusinessException("该商品不是待审核状态");
        }
        if (dto.getApproved()) {
            product.setAuditStatus(AuditStatusConstant.APPROVED);
            product.setProductStatus(ProductStatusConstant.ON_SALE);
            log.info("商品审核通过, productId={}", dto.getProductId());
        } else {
            product.setAuditStatus(AuditStatusConstant.REJECTED);
            product.setProductStatus(ProductStatusConstant.PENDING);
            log.info("商品审核拒绝, productId={}, reason={}", dto.getProductId(), dto.getReason());
        }
        updateById(product);
    }

    @Override
    @Transactional
    public void offShelf(Long productId) {
        Product product = getById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (!product.getProductStatus().equals(ProductStatusConstant.ON_SALE)) {
            throw new BusinessException("只有上架商品才能下架");
        }
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Product::getId, productId)
               .set(Product::getProductStatus, ProductStatusConstant.OFF_SHELF);
        baseMapper.update(null, wrapper);
        log.info("商品已下架, productId={}", productId);
    }

    @Override
    @Transactional
    public void cancelProduct(Long productId) {
        log.info("开始取消商品发布, productId={}", productId);
        Product product = getById(productId);
        if (product == null) {
            log.warn("取消商品发布失败：商品不存在, productId={}", productId);
            throw new BusinessException("商品不存在");
        }
        log.info("商品当前状态: productStatus={}, auditStatus={}", product.getProductStatus(), product.getAuditStatus());
        if (product.getProductStatus().equals(ProductStatusConstant.SOLD)) {
            throw new BusinessException("已售出商品不能取消");
        }
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Product::getId, productId)
               .set(Product::getProductStatus, ProductStatusConstant.CANCELLED);
        log.info("执行取消商品SQL更新, productId={}", productId);
        int rows = baseMapper.update(null, wrapper);
        log.info("取消商品发布完成, productId={}, affectedRows={}", productId, rows);
    }

    @Override
    @Transactional
    public void markSold(Long productId) {
        Product product = getById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Product::getId, productId)
               .set(Product::getProductStatus, ProductStatusConstant.SOLD);
        baseMapper.update(null, wrapper);
        log.info("商品已标记为售出, productId={}", productId);
    }

    private void sendStockAlert(Product product) {
        if (rocketMQTemplate == null) {
            return;
        }
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("productId", product.getId());
            alert.put("productName", product.getName());
            alert.put("currentStock", product.getStock());
            alert.put("threshold", product.getStockThreshold());
            rocketMQTemplate.syncSend(MqTopicConstant.STOCK_ALERT, alert);
            log.info("库存预警消息已发送, productId={}, stock={}, threshold={}",
                    product.getId(), product.getStock(), product.getStockThreshold());
        } catch (Exception e) {
            log.error("发送库存预警消息失败", e);
        }
    }
}
