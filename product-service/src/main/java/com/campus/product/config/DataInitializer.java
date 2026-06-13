package com.campus.product.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.product.entity.Category;
import com.campus.product.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类数据初始化器
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private CategoryService categoryService;

    @Override
    public void run(String... args) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        long count = categoryService.count(wrapper);

        if (count == 0) {
            log.info("========================================");
            log.info("开始初始化分类数据...");
            initDefaultCategories();
            log.info("分类数据初始化完成！");
            log.info("========================================");
        } else {
            log.info("分类数据已存在，跳过初始化");
        }
    }

    private void initDefaultCategories() {
        List<Category> categories = new ArrayList<>();

        // 教材书籍
        Category category1 = new Category();
        category1.setName("教材书籍");
        category1.setParentId(0L);
        category1.setSort(1);
        category1.setStatus(1);
        categories.add(category1);

        // 数码产品
        Category category2 = new Category();
        category2.setName("数码产品");
        category2.setParentId(0L);
        category2.setSort(2);
        category2.setStatus(1);
        categories.add(category2);

        // 生活用品
        Category category3 = new Category();
        category3.setName("生活用品");
        category3.setParentId(0L);
        category3.setSort(3);
        category3.setStatus(1);
        categories.add(category3);

        // 运动健身
        Category category4 = new Category();
        category4.setName("运动健身");
        category4.setParentId(0L);
        category4.setSort(4);
        category4.setStatus(1);
        categories.add(category4);

        // 服饰鞋包
        Category category5 = new Category();
        category5.setName("服饰鞋包");
        category5.setParentId(0L);
        category5.setSort(5);
        category5.setStatus(1);
        categories.add(category5);

        // 其他
        Category category6 = new Category();
        category6.setName("其他");
        category6.setParentId(0L);
        category6.setSort(6);
        category6.setStatus(1);
        categories.add(category6);

        categoryService.saveBatch(categories);
        log.info("已插入 {} 个分类", categories.size());
    }
}
