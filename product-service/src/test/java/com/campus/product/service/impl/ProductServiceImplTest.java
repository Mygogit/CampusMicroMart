package com.campus.product.service.impl;

import com.campus.common.constant.AuditStatusConstant;
import com.campus.common.constant.ProductStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.security.UserContext;
import com.campus.product.dto.AuditProductDTO;
import com.campus.product.dto.CreateProductDTO;
import com.campus.product.entity.Product;
import com.campus.product.mapper.ProductMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("商品服务单元测试")
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @InjectMocks
    private ProductServiceImpl productService;

    @Nested
    @DisplayName("商品创建")
    class CreateProductTests {

        @Test
        @DisplayName("创建商品成功")
        void createProductSuccess() {
            CreateProductDTO dto = new CreateProductDTO();
            dto.setName("高等数学教材");
            dto.setDescription("九成新");
            dto.setPrice(new BigDecimal("25.00"));
            dto.setStock(2);
            dto.setCategoryId(1L);
            dto.setDormitory("3号宿舍楼");

            try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
                userContext.when(UserContext::getCurrentUserId).thenReturn(1001L);

                Product result = productService.createProduct(dto);

                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("高等数学教材");
                assertThat(result.getProductStatus()).isEqualTo(ProductStatusConstant.PENDING);
                assertThat(result.getAuditStatus()).isEqualTo(AuditStatusConstant.PENDING);
                assertThat(result.getUserId()).isEqualTo(1001L);
                assertThat(result.getStockThreshold()).isEqualTo(5);
            }
        }

        @Test
        @DisplayName("创建商品时未登录应抛异常")
        void createProductNotLoggedIn() {
            CreateProductDTO dto = new CreateProductDTO();
            dto.setName("测试商品");

            try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
                userContext.when(UserContext::getCurrentUserId).thenReturn(null);

                assertThatThrownBy(() -> productService.createProduct(dto))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("用户未登录");
            }
        }
    }

    @Nested
    @DisplayName("库存管理")
    class StockTests {

        @Test
        @DisplayName("扣减库存成功")
        void deductStockSuccess() {
            Product product = buildProduct(1L, 10, ProductStatusConstant.ON_SALE);
            // after update stock becomes 9
            Product afterUpdate = buildProduct(1L, 9, ProductStatusConstant.ON_SALE);

            when(productMapper.selectById(1L))
                    .thenReturn(product).thenReturn(afterUpdate);
            when(productMapper.update(any(), any())).thenReturn(1);

            boolean result = productService.deductStock(1L, 1);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("扣减库存时商品不存在应抛异常")
        void deductStockProductNotFound() {
            when(productMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> productService.deductStock(999L, 1))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("商品不存在");
        }

        @Test
        @DisplayName("扣减库存时库存不足应抛异常")
        void deductStockInsufficient() {
            Product product = buildProduct(1L, 0, ProductStatusConstant.ON_SALE);
            when(productMapper.selectById(1L)).thenReturn(product);
            when(productMapper.update(any(), any())).thenReturn(0);

            assertThatThrownBy(() -> productService.deductStock(1L, 1))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不足");
        }

        @Test
        @DisplayName("库存扣减到0时自动下架")
        void deductStockToZeroAutoOffShelf() {
            Product before = buildProduct(1L, 1, ProductStatusConstant.ON_SALE);
            Product after = buildProduct(1L, 0, ProductStatusConstant.ON_SALE);

            when(productMapper.selectById(1L))
                    .thenReturn(before).thenReturn(after);
            when(productMapper.update(any(), any())).thenReturn(1);

            productService.deductStock(1L, 1);

            // verify off-shelf update is called
            verify(productMapper, atLeastOnce()).update(any(), any());
        }

        @Test
        @DisplayName("回滚库存成功")
        void rollbackStockSuccess() {
            Product restored = buildProduct(1L, 1, ProductStatusConstant.OFF_SHELF);
            when(productMapper.update(any(), any())).thenReturn(1);
            when(productMapper.selectById(1L)).thenReturn(restored);

            boolean result = productService.rollbackStock(1L, 1);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("商品审核")
    class AuditTests {

        @Test
        @DisplayName("审核通过")
        void auditApprove() {
            Product product = buildProduct(1L, 5, ProductStatusConstant.PENDING);
            product.setAuditStatus(AuditStatusConstant.PENDING);

            when(productMapper.selectById(1L)).thenReturn(product);
            when(productMapper.updateById(any(Product.class))).thenReturn(1);

            AuditProductDTO dto = new AuditProductDTO();
            dto.setProductId(1L);
            dto.setApproved(true);

            productService.auditProduct(dto);

            assertThat(product.getAuditStatus()).isEqualTo(AuditStatusConstant.APPROVED);
            assertThat(product.getProductStatus()).isEqualTo(ProductStatusConstant.ON_SALE);
        }

        @Test
        @DisplayName("审核拒绝")
        void auditReject() {
            Product product = buildProduct(1L, 5, ProductStatusConstant.PENDING);
            product.setAuditStatus(AuditStatusConstant.PENDING);

            when(productMapper.selectById(1L)).thenReturn(product);
            when(productMapper.updateById(any(Product.class))).thenReturn(1);

            AuditProductDTO dto = new AuditProductDTO();
            dto.setProductId(1L);
            dto.setApproved(false);
            dto.setReason("图片不清晰");

            productService.auditProduct(dto);

            assertThat(product.getAuditStatus()).isEqualTo(AuditStatusConstant.REJECTED);
        }

        @Test
        @DisplayName("审核非待审核状态商品应抛异常")
        void auditNotPending() {
            Product product = buildProduct(1L, 5, ProductStatusConstant.ON_SALE);
            product.setAuditStatus(AuditStatusConstant.APPROVED);

            when(productMapper.selectById(1L)).thenReturn(product);

            AuditProductDTO dto = new AuditProductDTO();
            dto.setProductId(1L);
            dto.setApproved(true);

            assertThatThrownBy(() -> productService.auditProduct(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("该商品不是待审核状态");
        }
    }

    @Nested
    @DisplayName("商品下架与取消")
    class OffShelfAndCancelTests {

        @Test
        @DisplayName("下架商品成功")
        void offShelfSuccess() {
            Product product = buildProduct(1L, 5, ProductStatusConstant.ON_SALE);
            when(productMapper.selectById(1L)).thenReturn(product);
            when(productMapper.update(any(), any())).thenReturn(1);

            productService.offShelf(1L);
            verify(productMapper).update(any(), any());
        }

        @Test
        @DisplayName("下架非上架商品应抛异常")
        void offShelfNotOnSale() {
            Product product = buildProduct(1L, 5, ProductStatusConstant.PENDING);
            when(productMapper.selectById(1L)).thenReturn(product);

            assertThatThrownBy(() -> productService.offShelf(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("只有上架商品才能下架");
        }

        @Test
        @DisplayName("取消已售出商品应抛异常")
        void cancelSoldProduct() {
            Product product = buildProduct(1L, 0, ProductStatusConstant.SOLD);
            when(productMapper.selectById(1L)).thenReturn(product);

            assertThatThrownBy(() -> productService.cancelProduct(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已售出商品不能取消");
        }

        @Test
        @DisplayName("标记售出成功")
        void markSoldSuccess() {
            Product product = buildProduct(1L, 0, ProductStatusConstant.ON_SALE);
            when(productMapper.selectById(1L)).thenReturn(product);
            when(productMapper.update(any(), any())).thenReturn(1);

            productService.markSold(1L);
            verify(productMapper).update(any(), any());
        }
    }

    // --- helper ---

    private Product buildProduct(Long id, int stock, int productStatus) {
        Product product = new Product();
        product.setId(id);
        product.setName("测试商品");
        product.setDescription("测试描述");
        product.setPrice(new BigDecimal("25.00"));
        product.setStock(stock);
        product.setStockThreshold(5);
        product.setProductStatus(productStatus);
        product.setAuditStatus(AuditStatusConstant.PENDING);
        product.setCategoryId(1L);
        product.setUserId(1001L);
        return product;
    }
}
