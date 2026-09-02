package com.scuplus.module.course.service;

import com.scuplus.common.result.PageResult;
import com.scuplus.module.course.dto.CourseVO;

public interface CourseView {
    PageResult<CourseVO> list(Long userid,int page,int size,String className,String teacherName);

}
