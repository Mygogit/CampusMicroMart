package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_credit_log")
public class CreditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String changeType;

    private Integer scoreChange;

    private Integer scoreAfter;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
