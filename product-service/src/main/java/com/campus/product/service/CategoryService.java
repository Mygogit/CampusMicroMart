package com.campus.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.product.entity.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {
    List<Category> getCategoryList();
}
