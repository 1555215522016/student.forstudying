package com.scuplus.module.course.service;

import com.scuplus.module.course.dto.CourseChooseVO;
import com.scuplus.module.course.dto.CourseDeleteVO;

public interface CourseSeckill {




    CourseChooseVO choose(Long userId,Long courseId);

    CourseDeleteVO delete(Long userId,Long courseId);
}
