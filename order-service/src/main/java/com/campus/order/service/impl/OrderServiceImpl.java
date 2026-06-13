package com.campus.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.constant.OrderStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.feign.ProductFeignClient;
import com.campus.common.mq.OrderStatusMessage;
import com.campus.common.result.Result;
import com.campus.common.security.UserContext;
import com.campus.order.dto.BatchUpdateOrderStatusDTO;
import com.campus.order.dto.CreateOrderDTO;
import com.campus.order.dto.OrderItemDTO;
import com.campus.order.entity.Order;
import com.campus.order.entity.OrderItem;
import com.campus.order.entity.Shipping;
import com.campus.order.mapper.OrderItemMapper;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.mapper.ShippingMapper;
import com.campus.order.mq.OrderStatusProducer;
import com.campus.order.service.OrderService;
import io.seata.spring.annotation.GlobalTransactional;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Observed
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired(required = false)
    private OrderStatusProducer orderStatusProducer;
    @Autowired
    private ShippingMapper shippingMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;

    @Override
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    @Transactional
    public Order createOrder(CreateOrderDTO createOrderDTO) {
        // 解析订单商品列表：优先使用多商品 items，否则回退到单商品字段
        List<OrderItemDTO> itemDTOs = buildItemList(createOrderDTO);

        // 扣减每个商品的库存
        for (OrderItemDTO item : itemDTOs) {
            Result<Boolean> deductResult = productFeignClient.deductStock(item.getProductId(), item.getQuantity());
            if (deductResult == null || !Integer.valueOf(200).equals(deductResult.getCode())) {
                log.error("扣减库存失败, productId={}, quantity={}, result={}",
                        item.getProductId(), item.getQuantity(), deductResult);
                throw new BusinessException("扣减库存失败: "
                        + (deductResult != null ? deductResult.getMessage() : "服务不可用"));
            }
        }

        // 计算总金额
        BigDecimal totalAmount = itemDTOs.stream()
                .map(i -> i.getUnitPrice().multiply(new BigDecimal(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 保存订单主表
        OrderItemDTO firstItem = itemDTOs.get(0);
        Order order = new Order();
        order.setOrderNo(IdUtil.simpleUUID());
        order.setUserId(createOrderDTO.getUserId());
        order.setProductId(firstItem.getProductId());
        order.setProductName(firstItem.getProductId().toString());
        order.setPrice(firstItem.getUnitPrice());
        order.setQuantity(itemDTOs.stream().mapToInt(OrderItemDTO::getQuantity).sum());
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatusConstant.WAITING_PAY);
        order.setShippingAddress(createOrderDTO.getShippingAddress());
        order.setBuyerPhone(createOrderDTO.getBuyerPhone());
        order.setRemark(createOrderDTO.getRemark());
        save(order);
        log.info("订单主表创建成功, orderId={}, orderNo={}", order.getId(), order.getOrderNo());

        // 保存订单商品明细
        for (OrderItemDTO itemDTO : itemDTOs) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(itemDTO.getProductId());
            orderItem.setQuantity(itemDTO.getQuantity());
            orderItem.setUnitPrice(itemDTO.getUnitPrice());
            orderItem.setTotalPrice(itemDTO.getUnitPrice().multiply(new BigDecimal(itemDTO.getQuantity())));
            orderItemMapper.insert(orderItem);
        }
        log.info("订单商品明细保存完成, orderId={}, itemCount={}", order.getId(), itemDTOs.size());

        return order;
    }

    /**
     * 从 DTO 构建商品列表，兼容单商品和多商品两种模式。
     */
    private List<OrderItemDTO> buildItemList(CreateOrderDTO dto) {
        if (!CollectionUtils.isEmpty(dto.getItems())) {
            return dto.getItems();
        }
        // 单商品兼容模式
        if (dto.getProductId() == null) {
            throw new BusinessException("商品ID不能为空");
        }
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(dto.getProductId());
        item.setUnitPrice(dto.getPrice());
        item.setQuantity(dto.getQuantity());
        return Collections.singletonList(item);
    }

    @Override
    @Transactional
    public boolean updateOrderStatus(Long orderId, Integer status) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!OrderStatusConstant.isValidTransition(order.getStatus(), status)) {
            throw new BusinessException("订单状态不允许从 " + order.getStatus() + " 变更为 " + status);
        }
        Integer oldStatus = order.getStatus();
        order.setStatus(status);
        boolean result = updateById(order);
        if (result && orderStatusProducer != null) {
            orderStatusProducer.sendOrderStatusChange(new OrderStatusMessage(
                    order.getId(), order.getOrderNo(), oldStatus, status, LocalDateTime.now()));
        }
        return result;
    }

    @Override
    @Transactional
    public int batchUpdateOrderStatus(BatchUpdateOrderStatusDTO dto) {
        List<Long> failedIds = new ArrayList<>();
        int successCount = 0;
        for (Long orderId : dto.getOrderIds()) {
            try {
                updateOrderStatus(orderId, dto.getStatus());
                successCount++;
            } catch (BusinessException e) {
                log.warn("批量更新订单状态失败, orderId={}, reason={}", orderId, e.getMessage());
                failedIds.add(orderId);
            }
        }
        if (!failedIds.isEmpty()) {
            throw new BusinessException("部分订单状态更新失败, 失败ID: " + failedIds);
        }
        log.info("批量更新订单状态完成, total={}, success={}", dto.getOrderIds().size(), successCount);
        return successCount;
    }

    @Override
    @Transactional
    public boolean cancelOrder(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getStatus().equals(OrderStatusConstant.WAITING_PAY)) {
            throw new BusinessException("订单状态不允许取消");
        }
        // 回滚所有订单商品的库存
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (CollectionUtils.isEmpty(orderItems)) {
            // 兼容旧数据：回滚主表商品库存
            Result<Boolean> rollbackResult = productFeignClient.rollbackStock(
                    order.getProductId(), order.getQuantity());
            if (rollbackResult == null || !Integer.valueOf(200).equals(rollbackResult.getCode())) {
                log.error("回滚库存失败, orderId={}, result={}", orderId, rollbackResult);
                throw new BusinessException("回滚库存失败");
            }
        } else {
            for (OrderItem item : orderItems) {
                Result<Boolean> rollbackResult = productFeignClient.rollbackStock(
                        item.getProductId(), item.getQuantity());
                if (rollbackResult == null || !Integer.valueOf(200).equals(rollbackResult.getCode())) {
                    log.error("回滚库存失败, orderId={}, productId={}, result={}",
                            orderId, item.getProductId(), rollbackResult);
                    throw new BusinessException("回滚库存失败");
                }
            }
        }
        Integer oldStatus = order.getStatus();
        order.setStatus(OrderStatusConstant.CANCELLED);
        boolean result = updateById(order);
        if (result && orderStatusProducer != null) {
            orderStatusProducer.sendOrderStatusChange(new OrderStatusMessage(
                    order.getId(), order.getOrderNo(), oldStatus, OrderStatusConstant.CANCELLED, LocalDateTime.now()));
        }
        log.info("订单已取消, orderId={}", orderId);
        return result;
    }

    @Override
    @Transactional
    public boolean shipOrder(Long orderId, String trackingNo, String carrier) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getStatus().equals(OrderStatusConstant.PAID)) {
            throw new BusinessException("仅已支付订单可以发货");
        }
        Shipping shipping = new Shipping();
        shipping.setOrderId(order.getId());
        shipping.setOrderNo(order.getOrderNo());
        shipping.setTrackingNo(trackingNo);
        shipping.setCarrier(carrier);
        shipping.setAddress(order.getShippingAddress());
        shipping.setShippedTime(LocalDateTime.now());
        shippingMapper.insert(shipping);
        updateOrderStatus(orderId, OrderStatusConstant.SHIPPED);
        log.info("订单已发货, orderId={}, trackingNo={}", orderId, trackingNo);
        return true;
    }

    @Override
    @Transactional
    public boolean confirmDelivery(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getStatus().equals(OrderStatusConstant.SHIPPED)) {
            throw new BusinessException("仅已发货订单可以确认收货");
        }
        updateOrderStatus(orderId, OrderStatusConstant.COMPLETED);

        try {
            productFeignClient.markSold(order.getProductId());
        } catch (Exception e) {
            log.error("标记商品已售出失败, orderId={}, productId={}", orderId, order.getProductId(), e);
        }

        LambdaQueryWrapper<Shipping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shipping::getOrderId, orderId);
        Shipping shipping = shippingMapper.selectOne(wrapper);
        if (shipping != null) {
            shipping.setDeliveredTime(LocalDateTime.now());
            shippingMapper.updateById(shipping);
        }

        log.info("订单已确认收货, orderId={}", orderId);
        return true;
    }

    @Override
    public IPage<Order> getMyOrders(Integer page, Integer size) {
        Long userId = UserContext.getCurrentUserId();
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
               .orderByDesc(Order::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public void exportExcel(OutputStream outputStream) {
        List<Order> orders = list();
        EasyExcel.write(outputStream, Order.class)
                .sheet("订单数据")
                .doWrite(orders);
        log.info("订单Excel导出完成, count={}", orders.size());
    }
}
