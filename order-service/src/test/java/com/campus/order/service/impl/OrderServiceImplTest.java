package com.campus.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.constant.OrderStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.feign.ProductFeignClient;
import com.campus.common.result.Result;
import com.campus.order.dto.CreateOrderDTO;
import com.campus.order.dto.OrderItemDTO;
import com.campus.order.entity.Order;
import com.campus.order.entity.OrderItem;
import com.campus.order.mapper.OrderItemMapper;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.mapper.ShippingMapper;
import com.campus.order.mq.OrderStatusProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("订单服务单元测试")
class OrderServiceImplTest {

    @Mock
    private ProductFeignClient productFeignClient;
    @Mock
    private OrderStatusProducer orderStatusProducer;
    @Mock
    private ShippingMapper shippingMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderMapper orderMapper;
    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderDTO singleItemDTO;
    private CreateOrderDTO multiItemDTO;

    @BeforeEach
    void setUp() {
        singleItemDTO = new CreateOrderDTO();
        singleItemDTO.setUserId(1L);
        singleItemDTO.setProductId(100L);
        singleItemDTO.setProductName("测试教材");
        singleItemDTO.setPrice(new BigDecimal("50.00"));
        singleItemDTO.setQuantity(2);
        singleItemDTO.setShippingAddress("3号宿舍楼");
        singleItemDTO.setBuyerPhone("13800138000");

        OrderItemDTO item1 = new OrderItemDTO();
        item1.setProductId(100L);
        item1.setUnitPrice(new BigDecimal("50.00"));
        item1.setQuantity(1);

        OrderItemDTO item2 = new OrderItemDTO();
        item2.setProductId(200L);
        item2.setUnitPrice(new BigDecimal("30.00"));
        item2.setQuantity(2);

        multiItemDTO = new CreateOrderDTO();
        multiItemDTO.setUserId(1L);
        multiItemDTO.setItems(Arrays.asList(item1, item2));
        multiItemDTO.setShippingAddress("3号宿舍楼");
        multiItemDTO.setBuyerPhone("13800138000");
    }

    @Test
    @DisplayName("单商品下单成功")
    void createOrderSingleItemSuccess() {
        when(productFeignClient.deductStock(anyLong(), anyInt()))
                .thenReturn(Result.success(true));

        Order result = orderService.createOrder(singleItemDTO);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(OrderStatusConstant.WAITING_PAY);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(productFeignClient).deductStock(100L, 2);
        verify(orderItemMapper).insert(any(OrderItem.class));
    }

    @Test
    @DisplayName("多商品下单成功")
    void createOrderMultiItemSuccess() {
        when(productFeignClient.deductStock(anyLong(), anyInt()))
                .thenReturn(Result.success(true));

        Order result = orderService.createOrder(multiItemDTO);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatusConstant.WAITING_PAY);
        // 50 * 1 + 30 * 2 = 110
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("110.00"));
        verify(productFeignClient).deductStock(100L, 1);
        verify(productFeignClient).deductStock(200L, 2);
        verify(orderItemMapper, times(2)).insert(any(OrderItem.class));
    }

    @Test
    @DisplayName("下单时库存扣减失败应抛异常")
    void createOrderStockDeductFailed() {
        when(productFeignClient.deductStock(anyLong(), anyInt()))
                .thenReturn(Result.error("库存不足"));

        assertThatThrownBy(() -> orderService.createOrder(singleItemDTO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("扣减库存失败");
    }

    @Test
    @DisplayName("取消订单成功（多商品回滚库存）")
    void cancelOrderSuccess() {
        Order order = buildOrder(1L, OrderStatusConstant.WAITING_PAY);
        when(orderMapper.selectById(1L)).thenReturn(order);

        OrderItem item1 = buildOrderItem(1L, 100L, 1);
        OrderItem item2 = buildOrderItem(1L, 200L, 2);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(item1, item2));
        when(productFeignClient.rollbackStock(anyLong(), anyInt()))
                .thenReturn(Result.success(true));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        doNothing().when(orderStatusProducer).sendOrderStatusChange(any());

        boolean result = orderService.cancelOrder(1L);

        assertThat(result).isTrue();
        verify(productFeignClient).rollbackStock(100L, 1);
        verify(productFeignClient).rollbackStock(200L, 2);
    }

    @Test
    @DisplayName("取消非待支付状态的订单应失败")
    void cancelOrderInvalidStatus() {
        Order order = buildOrder(1L, OrderStatusConstant.PAID);
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单状态不允许取消");
    }

    @Test
    @DisplayName("更新订单状态")
    void updateOrderStatusSuccess() {
        Order order = buildOrder(1L, OrderStatusConstant.WAITING_PAY);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        doNothing().when(orderStatusProducer).sendOrderStatusChange(any());

        boolean result = orderService.updateOrderStatus(1L, OrderStatusConstant.PAID);

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatusConstant.PAID);
    }

    @Test
    @DisplayName("更新不存在的订单应抛异常")
    void updateOrderStatusNotFound() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.updateOrderStatus(999L, OrderStatusConstant.PAID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单不存在");
    }

    // --- helper methods ---

    private Order buildOrder(Long id, Integer status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("ORDER-" + id);
        order.setUserId(1L);
        order.setProductId(100L);
        order.setPrice(new BigDecimal("50.00"));
        order.setQuantity(2);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(status);
        order.setShippingAddress("测试地址");
        return order;
    }

    private OrderItem buildOrderItem(Long orderId, Long productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setTotalPrice(new BigDecimal("50.00").multiply(new BigDecimal(quantity)));
        return item;
    }
}
