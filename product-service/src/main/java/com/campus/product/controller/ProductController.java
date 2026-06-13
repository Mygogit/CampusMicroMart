package com.campus.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.constant.AuditStatusConstant;
import com.campus.common.constant.ProductStatusConstant;
import com.campus.common.result.Result;
import com.campus.common.security.UserContext;
import com.campus.product.dto.AuditProductDTO;
import com.campus.product.dto.CreateProductDTO;
import com.campus.product.dto.UpdateProductDTO;
import com.campus.product.entity.Product;
import com.campus.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "商品管理")
@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Operation(summary = "获取在售商品列表")
    @GetMapping("/list")
    public Result<IPage<Product>> list(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                       @RequestParam(name = "size", defaultValue = "10") Integer size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getAuditStatus, AuditStatusConstant.APPROVED)
               .eq(Product::getProductStatus, ProductStatusConstant.ON_SALE)
               .orderByDesc(Product::getCreateTime);
        return Result.success(productService.page(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "搜索商品")
    @GetMapping("/search")
    public Result<IPage<Product>> search(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                          @RequestParam(name = "size", defaultValue = "10") Integer size,
                                          @RequestParam(name = "categoryId", required = false) Long categoryId,
                                          @RequestParam(name = "courseCode", required = false) String courseCode,
                                          @RequestParam(name = "dormitory", required = false) String dormitory) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getAuditStatus, AuditStatusConstant.APPROVED)
               .eq(Product::getProductStatus, ProductStatusConstant.ON_SALE);
        if (categoryId != null) wrapper.eq(Product::getCategoryId, categoryId);
        if (courseCode != null && !courseCode.isEmpty()) wrapper.eq(Product::getCourseCode, courseCode);
        if (dormitory != null && !dormitory.isEmpty()) wrapper.eq(Product::getDormitory, dormitory);
        wrapper.orderByDesc(Product::getCreateTime);
        return Result.success(productService.page(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "获取当前用户的商品列表（含所有审核状态）")
    @GetMapping("/my")
    public Result<IPage<Product>> getMyProducts(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                                 @RequestParam(name = "size", defaultValue = "10") Integer size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getUserId, userId)
               .orderByDesc(Product::getCreateTime);
        return Result.success(productService.page(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "获取商品详情")
    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @Operation(summary = "新增商品")
    @PostMapping
    public Result<Product> save(@Valid @RequestBody CreateProductDTO dto) {
        return Result.success(productService.createProduct(dto));
    }

    @Operation(summary = "更新商品")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody UpdateProductDTO dto) {
        Product product = productService.getById(dto.getId());
        if (product == null) {
            return Result.error("商品不存在");
        }
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setProductStatus(dto.getProductStatus());
        product.setCategoryId(dto.getCategoryId());
        product.setImages(dto.getImages());
        product.setCourseCode(dto.getCourseCode());
        product.setDormitory(dto.getDormitory());
        productService.updateById(product);
        return Result.success();
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "管理员审核商品")
    @PostMapping("/audit")
    public Result<Void> audit(@Valid @RequestBody AuditProductDTO dto) {
        productService.auditProduct(dto);
        return Result.success();
    }

    @Operation(summary = "管理员查看待审核商品")
    @GetMapping("/pending")
    public Result<IPage<Product>> getPending(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                              @RequestParam(name = "size", defaultValue = "10") Integer size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getAuditStatus, AuditStatusConstant.PENDING)
               .orderByAsc(Product::getCreateTime);
        return Result.success(productService.page(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "卖家下架商品")
    @PutMapping("/{id}/off-shelf")
    public Result<Void> offShelf(@PathVariable Long id) {
        productService.offShelf(id);
        return Result.success();
    }

    @Operation(summary = "卖家取消发布")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        log.info("接收到取消商品发布请求, productId={}", id);
        productService.cancelProduct(id);
        log.info("取消商品发布成功, productId={}", id);
        return Result.success();
    }

    @Operation(summary = "扣减库存")
    @PostMapping("/deduct")
    public Result<Boolean> deductStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity) {
        return Result.success(productService.deductStock(productId, quantity));
    }

    @Operation(summary = "回滚库存")
    @PostMapping("/rollback")
    public Result<Boolean> rollbackStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity) {
        return Result.success(productService.rollbackStock(productId, quantity));
    }

    @Operation(summary = "标记商品已售出")
    @PutMapping("/{id}/mark-sold")
    public Result<Void> markSold(@PathVariable Long id) {
        productService.markSold(id);
        return Result.success();
    }
}
