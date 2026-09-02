package com.scuplus.module.course.controller;


import com.scuplus.common.result.PageResult;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.course.dto.CourseVO;
import com.scuplus.module.course.service.CourseView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/course")
public class CourseViewController {
    private final CourseView courseView;
    @GetMapping
    public Result<PageResult<CourseVO>> list(@RequestParam int page,
                                             @RequestParam int size,
                                             @RequestParam (required = false) String className,
                                             @RequestParam (required = false) String teacherName
    ){
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext()
                .getAuthentication();
        Long userid= loginUser.getUserId();
        PageResult<CourseVO> courses=courseView.list(userid,page,size,className,teacherName);
        return Result.success(courses);
    }

}
