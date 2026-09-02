package com.scuplus.module.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CourseChooseRequest {
    @NotNull(message = "课程id不得为空")
    private Long courseID;
}
