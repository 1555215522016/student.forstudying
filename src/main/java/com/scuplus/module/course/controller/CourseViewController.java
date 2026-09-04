package com.scuplus.module.course.controller;


import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.PageResult;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.course.dto.CourseVO;
import com.scuplus.module.course.service.CourseView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
        // 取 principal 而不是整个 Authentication：LoginUser 是认证主体，Authentication 是 UsernamePasswordAuthenticationToken
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        PageResult<CourseVO> courses = courseView.list(loginUser.getUserId(), page, size, className, teacherName);
        return Result.success(courses);
    }

}
