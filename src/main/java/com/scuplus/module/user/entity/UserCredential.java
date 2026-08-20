package com.scuplus.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("t_user_credential")
public class UserCredential {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type;
    private String account;
    private String encryptedPassword;
    private LocalDateTime createdAt;


}
