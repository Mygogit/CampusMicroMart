package com.campus.common.constant;

public interface RedisConstant {

    String PRODUCT_KEY = "product:";
    String ORDER_KEY = "order:";
    String USER_KEY = "user:";
    String USER_TOKEN_PREFIX = "user:token:";
    String CATEGORY_KEY = "category:";

    Long PRODUCT_EXPIRE_TIME = 3600L;
    Long ORDER_EXPIRE_TIME = 1800L;
    Long USER_EXPIRE_TIME = 7200L;
    Long CATEGORY_EXPIRE_TIME = 7200L;
}

