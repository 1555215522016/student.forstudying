package com.scuplus.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scuplus.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
