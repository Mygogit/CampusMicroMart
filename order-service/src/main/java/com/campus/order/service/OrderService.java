package com.campus.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.order.dto.BatchUpdateOrderStatusDTO;
import com.campus.order.dto.CreateOrderDTO;
import com.campus.order.entity.Order;

import java.io.OutputStream;

public interface OrderService extends IService<Order> {
    Order createOrder(CreateOrderDTO createOrderDTO);
    boolean updateOrderStatus(Long orderId, Integer status);
    boolean cancelOrder(Long orderId);
    boolean shipOrder(Long orderId, String trackingNo, String carrier);
    boolean confirmDelivery(Long orderId);
    IPage<Order> getMyOrders(Integer page, Integer size);
    int batchUpdateOrderStatus(BatchUpdateOrderStatusDTO dto);
    void exportExcel(OutputStream outputStream);
}
