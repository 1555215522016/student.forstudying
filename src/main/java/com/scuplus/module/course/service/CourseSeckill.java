package com.scuplus.module.course.service;

import com.scuplus.module.course.dto.CourseChooseVO;
import com.scuplus.module.course.dto.CourseDeleteVO;

public interface CourseSeckill {




    CourseChooseVO choose(Long userId,Long courseId);

    CourseDeleteVO delete(Long userId,Long courseId);

    /** 读取某课实时剩余名额（缓存缺失自愈；DB 也不可用时返回 null） */
    Integer remainingOf(Long courseId);
}
