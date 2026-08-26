package com.scuplus.module.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scuplus.module.share.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PostMapper extends BaseMapper<Post> {
    @Select("SELECT COUNT(*) FROM t_post WHERE media_urls LIKE #{pattern}")
    int countByMediaUrl(@Param("pattern") String pattern);
}
