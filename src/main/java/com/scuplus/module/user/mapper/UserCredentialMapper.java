package com.scuplus.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scuplus.module.user.entity.UserCredential;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户凭证 Mapper：操作 t_user_credential 表
 * 泛型 UserCredential = 实体（不是 Mapper 自己！）
 */
@Mapper
public interface UserCredentialMapper extends BaseMapper<UserCredential> {
}
