package com.campus.product.controller;

import com.campus.common.result.Result;
import com.campus.product.entity.Category;
import com.campus.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "商品分类管理")
@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Operation(summary = "获取分类列表")
    @GetMapping("/list")
    public Result<List<Category>> getCategoryList() {
        return Result.success(categoryService.getCategoryList());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<Void> addCategory(@RequestBody Category category) {
        categoryService.save(category);
        return Result.success();
    }

    @Operation(summary = "更新分类")
    @PutMapping
    public Result<Void> updateCategory(@RequestBody Category category) {
        categoryService.updateById(category);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.removeById(id);
        return Result.success();
    }
}
