package com.scuplus.module.course.controller;

import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.course.dto.CourseChooseRequest;
import com.scuplus.module.course.dto.CourseChooseVO;
import com.scuplus.module.course.dto.CourseDeleteVO;
import com.scuplus.module.course.mapper.CourseSelectionMapper;
import com.scuplus.module.course.service.CourseSeckill;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/course")
@RequiredArgsConstructor
public class CourseSeckillController {

    private final CourseSeckill courseSeckill;

    @PostMapping
    public Result<CourseChooseVO> choose
            (@Valid @RequestBody CourseChooseRequest request){
        Long userId=getUserId();
        CourseChooseVO vo=courseSeckill.choose(userId, request.getCourseID());
        return Result.success(vo);
    }

    @PostMapping("/delete/{courseId}")
    public Result<CourseDeleteVO> delete(@PathVariable Long courseId){
        Long userId=getUserId();
        CourseDeleteVO vo=courseSeckill.delete(userId,courseId);
        return Result.success(vo);
    }

    private Long getUserId(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

}
