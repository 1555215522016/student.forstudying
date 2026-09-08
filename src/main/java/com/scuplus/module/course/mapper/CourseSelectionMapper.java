package com.scuplus.module.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scuplus.module.course.entiy.CourseSelection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseSelectionMapper extends BaseMapper<CourseSelection> {

    /** PHASE2 判时间冲突：该用户已选(status=1)的课程里，有没有上课时间撞上目标课的 */
    @Select("SELECT COUNT(*) FROM t_course_selection s " +
            "JOIN t_course c ON s.course_id = c.id " +
            "WHERE s.user_id = #{userId} AND s.status = 1 " +
            "AND c.class_time = #{classTime} AND c.deleted = 0")
    long selectConflictedCount(@Param("userId") Long userId, @Param("classTime") String classTime);
}
