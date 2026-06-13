package com.campus.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.Result;
import com.campus.common.security.UserContext;
import com.campus.order.dto.BatchUpdateOrderStatusDTO;
import com.campus.order.dto.CreateOrderDTO;
import com.campus.order.dto.UpdateOrderDTO;
import com.campus.order.entity.Order;
import com.campus.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@Tag(name = "订单管理")
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Operation(summary = "获取订单列表")
    @GetMapping("/list")
    public Result<IPage<Order>> list(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                     @RequestParam(name = "size", defaultValue = "10") Integer size) {
        return Result.success(orderService.page(new Page<>(page, size)));
    }

    @Operation(summary = "我的订单")
    @GetMapping("/my")
    public Result<IPage<Order>> myOrders(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                          @RequestParam(name = "size", defaultValue = "10") Integer size) {
        return Result.success(orderService.getMyOrders(page, size));
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/{id}")
    public Result<Order> getById(@PathVariable Long id) {
        return Result.success(orderService.getById(id));
    }

    @Operation(summary = "创建订单")
    @PostMapping
    public Result<Order> createOrder(@Valid @RequestBody CreateOrderDTO createOrderDTO) {
        return Result.success(orderService.createOrder(createOrderDTO));
    }

    @Operation(summary = "更新订单状态")
    @PutMapping("/status")
    public Result<Boolean> updateOrderStatus(@RequestParam Long orderId, @RequestParam Integer status) {
        return Result.success(orderService.updateOrderStatus(orderId, status));
    }

    @Operation(summary = "批量更新订单状态")
    @PostMapping("/batch/status")
    public Result<Integer> batchUpdateOrderStatus(@Valid @RequestBody BatchUpdateOrderStatusDTO dto) {
        return Result.success(orderService.batchUpdateOrderStatus(dto));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/cancel")
    public Result<Boolean> cancelOrder(@RequestParam Long orderId) {
        return Result.success(orderService.cancelOrder(orderId));
    }

    @Operation(summary = "管理员发货")
    @PostMapping("/{id}/ship")
    public Result<Boolean> shipOrder(@PathVariable Long id,
                                      @RequestParam String trackingNo,
                                      @RequestParam String carrier) {
        return Result.success(orderService.shipOrder(id, trackingNo, carrier));
    }

    @Operation(summary = "买家确认收货")
    @PostMapping("/{id}/confirm")
    public Result<Boolean> confirmDelivery(@PathVariable Long id) {
        return Result.success(orderService.confirmDelivery(id));
    }

    @Operation(summary = "导出订单CSV")
    @GetMapping("/export")
    public void exportOrders(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=GBK");
        response.setHeader("Content-Disposition", "attachment; filename=orders.csv");
        PrintWriter writer = response.getWriter();
        writer.println("订单ID,订单编号,用户ID,商品ID,商品名称,单价,数量,总金额,状态,收货地址,联系电话,备注,创建时间");
        List<Order> orders = orderService.list();
        for (Order o : orders) {
            writer.printf("%d,%s,%d,%d,%s,%s,%d,%s,%d,%s,%s,%s,%s%n",
                    o.getId(), o.getOrderNo(), o.getUserId(), o.getProductId(),
                    o.getProductName(), o.getPrice(), o.getQuantity(), o.getTotalAmount(),
                    o.getStatus(),
                    o.getShippingAddress() != null ? o.getShippingAddress() : "",
                    o.getBuyerPhone() != null ? o.getBuyerPhone() : "",
                    o.getRemark() != null ? o.getRemark() : "",
                    o.getCreateTime());
        }
        writer.flush();
    }

    @Operation(summary = "导出订单Excel")
    @GetMapping("/export/excel")
    public void exportOrdersExcel(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=orders.xlsx");
        orderService.exportExcel(response.getOutputStream());
    }

    @Operation(summary = "更新订单")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody UpdateOrderDTO dto) {
        Order order = orderService.getById(dto.getId());
        if (order == null) {
            return Result.error("订单不存在");
        }
        order.setRemark(dto.getRemark());
        orderService.updateById(order);
        return Result.success();
    }

    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orderService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "检查订单是否存在")
    @GetMapping("/exists/{id}")
    public Result<Boolean> exists(@PathVariable Long id) {
        return Result.success(orderService.getById(id) != null);
    }
}
