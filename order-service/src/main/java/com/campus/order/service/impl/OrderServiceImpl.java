package com.campus.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.constant.AuditStatusConstant;
import com.campus.common.constant.OrderStatusConstant;
import com.campus.common.constant.ProductStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.feign.ProductFeignClient;
import com.campus.common.feign.ProductInfo;
import com.campus.common.feign.UserFeignClient;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Observed
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired(required = false)
    private OrderStatusProducer orderStatusProducer;
    @Autowired
    private ShippingMapper shippingMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    @Qualifier("orderCreateTotal")
    private Counter orderCreateTotal;
    @Autowired
    @Qualifier("orderCreateSuccess")
    private Counter orderCreateSuccess;
    @Autowired
    @Qualifier("orderCreateFailure")
    private Counter orderCreateFailure;
    @Autowired
    @Qualifier("orderCreateDuration")
    private Timer orderCreateDuration;

    @Override
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    @Transactional
    public Order createOrder(CreateOrderDTO createOrderDTO) {
        orderCreateTotal.increment();
        long startNanos = System.nanoTime();
        try {
            Long buyerId = UserContext.getCurrentUserId();
            if (buyerId == null) {
                throw new BusinessException("请先登录");
            }

            // 解析订单商品列表（兼容单商品与多商品）
            List<OrderItemDTO> itemDTOs = buildItemList(createOrderDTO);
            if (CollectionUtils.isEmpty(itemDTOs)) {
                throw new BusinessException("订单商品不能为空");
            }

            // 1. 服务端取价：以商品真实价格计算金额，忽略前端传入的价格
            List<OrderItem> orderItems = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;
            int totalQuantity = 0;
            Long firstProductId = null;
            String firstProductName = null;

            for (OrderItemDTO item : itemDTOs) {
                if (item.getProductId() == null) {
                    throw new BusinessException("商品ID不能为空");
                }
                int qty = item.getQuantity() == null ? 1 : item.getQuantity();
                if (qty <= 0) {
                    throw new BusinessException("购买数量必须大于0");
                }
                Result<ProductInfo> infoResult = productFeignClient.getProductInfo(item.getProductId());
                if (infoResult == null || !Integer.valueOf(200).equals(infoResult.getCode())
                        || infoResult.getData() == null) {
                    throw new BusinessException("商品不存在或不可用");
                }
                ProductInfo info = infoResult.getData();
                if (!ProductStatusConstant.ON_SALE.equals(info.getProductStatus())
                        || !AuditStatusConstant.APPROVED.equals(info.getAuditStatus())) {
                    throw new BusinessException("商品不在售: " + info.getName());
                }
                BigDecimal unitPrice = info.getPrice() == null ? BigDecimal.ZERO : info.getPrice();
                BigDecimal subTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                OrderItem oi = new OrderItem();
                oi.setProductId(info.getId());
                oi.setQuantity(qty);
                oi.setUnitPrice(unitPrice);
                oi.setTotalPrice(subTotal);
                orderItems.add(oi);

                totalAmount = totalAmount.add(subTotal);
                totalQuantity += qty;
                if (firstProductId == null) {
                    firstProductId = info.getId();
                    firstProductName = info.getName();
                }
            }

            // 2. 扣减库存（远程调用，失败即中止）
            for (OrderItemDTO item : itemDTOs) {
                Result<Boolean> deductResult = productFeignClient.deductStock(item.getProductId(),
                        item.getQuantity() == null ? 1 : item.getQuantity());
                if (deductResult == null || !Integer.valueOf(200).equals(deductResult.getCode())) {
                    log.error("扣减库存失败, productId={}, quantity={}, result={}",
                            item.getProductId(), item.getQuantity(), deductResult);
                    throw new BusinessException("扣减库存失败");
                }
            }

            // 3. 保存订单主表 + 明细；失败时补偿回滚库存
            Order order = new Order();
            order.setOrderNo(IdUtil.simpleUUID());
            order.setUserId(buyerId);
            order.setProductId(firstProductId);
            order.setProductName(firstProductName);
            order.setPrice(orderItems.get(0).getUnitPrice());
            order.setQuantity(totalQuantity);
            order.setTotalAmount(totalAmount);
            order.setStatus(OrderStatusConstant.WAITING_PAY);
            order.setShippingAddress(createOrderDTO.getShippingAddress());
            order.setBuyerPhone(createOrderDTO.getBuyerPhone());
            order.setRemark(createOrderDTO.getRemark());

            try {
                save(order);
                for (OrderItem oi : orderItems) {
                    oi.setOrderId(order.getId());
                    orderItemMapper.insert(oi);
                }
            } catch (Exception e) {
                log.error("订单保存失败，补偿回滚库存, orderNo={}", order.getOrderNo(), e);
                for (OrderItemDTO item : itemDTOs) {
                    try {
                        productFeignClient.rollbackStock(item.getProductId(),
                                item.getQuantity() == null ? 1 : item.getQuantity());
                    } catch (Exception ex) {
                        log.error("补偿回滚库存失败, productId={}", item.getProductId(), ex);
                    }
                }
                throw e;
            }

            log.info("订单创建成功, orderId={}, orderNo={}, totalAmount={}, itemCount={}",
                    order.getId(), order.getOrderNo(), totalAmount, orderItems.size());
            orderCreateSuccess.increment();
            return order;
        } catch (Exception e) {
            orderCreateFailure.increment();
            throw e;
        } finally {
            orderCreateDuration.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 从 DTO 构建商品列表，兼容单商品和多商品两种模式（价格由服务端覆盖）。
     */
    private List<OrderItemDTO> buildItemList(CreateOrderDTO dto) {
        if (!CollectionUtils.isEmpty(dto.getItems())) {
            return dto.getItems();
        }
        if (dto.getProductId() == null) {
            throw new BusinessException("商品ID不能为空");
        }
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(dto.getProductId());
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
            Result<Boolean> rollbackResult = productFeignClient.rollbackStock(
                    order.getProductId(), order.getQuantity());
            if (rollbackResult == null || !Integer.valueOf(200).equals(rollbackResult.getCode())) {
                throw new BusinessException("回滚库存失败");
            }
        } else {
            for (OrderItem item : orderItems) {
                Result<Boolean> rollbackResult = productFeignClient.rollbackStock(
                        item.getProductId(), item.getQuantity());
                if (rollbackResult == null || !Integer.valueOf(200).equals(rollbackResult.getCode())) {
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

        // 结算：按订单明细，将货款结算给各卖家，并标记商品已售出
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (CollectionUtils.isEmpty(orderItems)) {
            settleItem(order.getProductId(), order.getTotalAmount());
        } else {
            for (OrderItem item : orderItems) {
                settleItem(item.getProductId(), item.getTotalPrice());
            }
        }

        updateOrderStatus(orderId, OrderStatusConstant.COMPLETED);

        LambdaQueryWrapper<Shipping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shipping::getOrderId, orderId);
        Shipping shipping = shippingMapper.selectOne(wrapper);
        if (shipping != null) {
            shipping.setDeliveredTime(LocalDateTime.now());
            shippingMapper.updateById(shipping);
        }

        log.info("订单已确认收货并完成结算, orderId={}", orderId);
        return true;
    }

    /**
     * 结算单个商品：给卖家入账 + 标记售出。
     */
    private void settleItem(Long productId, BigDecimal amount) {
        try {
            Result<ProductInfo> infoResult = productFeignClient.getProductInfo(productId);
            if (infoResult != null && Integer.valueOf(200).equals(infoResult.getCode())
                    && infoResult.getData() != null && infoResult.getData().getUserId() != null) {
                Result<Boolean> creditResult = userFeignClient.creditDeposit(
                        infoResult.getData().getUserId(), amount);
                if (creditResult != null && !Integer.valueOf(200).equals(creditResult.getCode())) {
                    log.error("卖家结算入账失败, sellerId={}, amount={}, result={}",
                            infoResult.getData().getUserId(), amount, creditResult);
                }
            }
        } catch (Exception e) {
            log.error("卖家结算异常, productId={}", productId, e);
        }
        try {
            productFeignClient.markSold(productId);
        } catch (Exception e) {
            log.error("标记商品已售出失败, productId={}", productId, e);
        }
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
    public BigDecimal getOrderAmount(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order.getTotalAmount();
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
