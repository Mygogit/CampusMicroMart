package com.campus.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.order.entity.Shipping;
import com.campus.order.mapper.ShippingMapper;
import com.campus.order.service.ShippingService;
import org.springframework.stereotype.Service;

@Service
public class ShippingServiceImpl extends ServiceImpl<ShippingMapper, Shipping> implements ShippingService {
}
