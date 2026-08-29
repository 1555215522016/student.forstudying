package com.scuplus.module.course.entiy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String teacher;

    private Long capacity;

    private Long selectedCount;

    private String classTime;

    private Integer status;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


}
