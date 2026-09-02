package com.scuplus.module.course.entiy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("t_course_selection")
@Data
public class CourseSelection {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long courseId;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
