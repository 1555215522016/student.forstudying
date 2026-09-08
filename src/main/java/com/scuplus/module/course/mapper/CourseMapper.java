package com.scuplus.module.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scuplus.module.course.entiy.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /** PHASE2 选课锁行用：行锁串行化同一门课的选课，保证 COUNT 判量不并发错乱 */
    @Select("SELECT * FROM t_course WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Course selectByIdForUpdate(@Param("id") Long id);
}
