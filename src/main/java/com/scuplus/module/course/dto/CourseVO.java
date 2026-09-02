package com.scuplus.module.course.dto;

import lombok.Data;

@Data
public class CourseVO {
    /** 课程 ID（前端抢课要传它，必须有） */
    private Long id;
    private String teacher;
    private String classTime;
    private Long capacity;
    private String name;
    /** 已选人数；-1 表示当前读不到（降级），前端显示"名额紧张" */
    private Long selectedCount;
    /** 当前用户是否已选该课（前端用它置灰"已抢"） */
    private Boolean ifchoosen;
}
