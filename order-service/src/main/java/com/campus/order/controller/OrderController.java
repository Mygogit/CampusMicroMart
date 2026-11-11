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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "订单管理")
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Operation(summary = "获取全部订单（管理员）")
    @GetMapping("/list")
    public Result<IPage<Order>> list(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                     @RequestParam(name = "size", defaultValue = "10") Integer size) {
        if (!UserContext.isAdmin()) {
            return Result.error(403, "仅管理员可查看全部订单");
        }
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
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!isOwnerOrAdmin(order)) {
            return Result.error(403, "无权查看该订单");
        }
        return Result.success(order);
    }

    @Operation(summary = "创建订单")
    @PostMapping
    public Result<Order> createOrder(@Valid @RequestBody CreateOrderDTO createOrderDTO) {
        return Result.success(orderService.createOrder(createOrderDTO));
    }

    @Operation(summary = "更新订单状态（内部调用）")
    @PutMapping("/status")
    public Result<Boolean> updateOrderStatus(@RequestParam Long orderId, @RequestParam Integer status) {
        return Result.success(orderService.updateOrderStatus(orderId, status));
    }

    @Operation(summary = "批量更新订单状态（管理员）")
    @PostMapping("/batch/status")
    public Result<Integer> batchUpdateOrderStatus(@Valid @RequestBody BatchUpdateOrderStatusDTO dto) {
        return Result.success(orderService.batchUpdateOrderStatus(dto));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/cancel")
    public Result<Boolean> cancelOrder(@RequestParam Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!isOwnerOrAdmin(order)) {
            return Result.error(403, "无权操作该订单");
        }
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
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!isOwnerOrAdmin(order)) {
            return Result.error(403, "无权操作该订单");
        }
        return Result.success(orderService.confirmDelivery(id));
    }

    @Operation(summary = "导出订单CSV（管理员）")
    @GetMapping("/export")
    public void exportOrders(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=orders.csv");
        PrintWriter writer = response.getWriter();
        List<String> header = List.of("订单ID", "订单编号", "用户ID", "商品ID", "商品名称", "单价", "数量", "总金额", "状态", "收货地址", "联系电话", "备注", "创建时间");
        writer.println(String.join(",", header.stream().map(this::csv).toList()));
        List<Order> orders = orderService.list();
        for (Order o : orders) {
            List<String> row = new ArrayList<>();
            row.add(String.valueOf(o.getId()));
            row.add(o.getOrderNo());
            row.add(String.valueOf(o.getUserId()));
            row.add(String.valueOf(o.getProductId()));
            row.add(o.getProductName());
            row.add(String.valueOf(o.getPrice()));
            row.add(String.valueOf(o.getQuantity()));
            row.add(String.valueOf(o.getTotalAmount()));
            row.add(String.valueOf(o.getStatus()));
            row.add(o.getShippingAddress());
            row.add(o.getBuyerPhone());
            row.add(o.getRemark());
            row.add(String.valueOf(o.getCreateTime()));
            writer.println(String.join(",", row.stream().map(this::csv).toList()));
        }
        writer.flush();
    }

    @Operation(summary = "导出订单Excel（管理员）")
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
        if (!isOwnerOrAdmin(order)) {
            return Result.error(403, "无权操作该订单");
        }
        order.setRemark(dto.getRemark());
        orderService.updateById(order);
        return Result.success();
    }

    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!isOwnerOrAdmin(order)) {
            return Result.error(403, "无权操作该订单");
        }
        orderService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "检查订单是否存在（内部调用）")
    @GetMapping("/exists/{id}")
    public Result<Boolean> exists(@PathVariable Long id) {
        return Result.success(orderService.getById(id) != null);
    }

    @Operation(summary = "获取订单应付金额（内部调用）")
    @GetMapping("/{id}/amount")
    public Result<BigDecimal> getAmount(@PathVariable Long id) {
        return Result.success(orderService.getOrderAmount(id));
    }

    @Operation(summary = "内部取消订单（支付超时/重试耗尽，内部调用）")
    @PostMapping("/{id}/cancel-internal")
    public Result<Boolean> cancelInternal(@PathVariable Long id) {
        return Result.success(orderService.cancelOrder(id));
    }

    private boolean isOwnerOrAdmin(Order order) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return userId.equals(order.getUserId()) || UserContext.isAdmin();
    }

    /**
     * CSV 字段转义：防公式注入 + 逗号/引号/换行转义。
     */
    private String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        char first = s.isEmpty() ? 0 : s.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
