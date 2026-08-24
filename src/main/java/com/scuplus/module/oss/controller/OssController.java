package com.scuplus.module.oss.controller;

import cn.hutool.core.util.IdUtil;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.oss.dto.PresignDTO;
import com.scuplus.module.oss.service.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OSS 接口（需登录）
 *
 * POST /api/v1/oss/presign?fileName=xxx.jpg
 *   → 返回 {presignedUrl(上传), objectUrl(访问)}
 */
@RestController
@RequestMapping("/api/v1/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssService ossService;

    /**
     * 获取上传链接（前端拿到后直接 PUT 文件到 MinIO）
     *
     * @param fileName 原始文件名（用于提取扩展名）
     */
    @PostMapping("/presign")
    public Result<PresignDTO> presign(@RequestParam String fileName) {
        LoginUser user = currentUser();
        // 存储路径 posts/{userId}/{uuid}.{ext}：按用户分目录 + 随机名，避免重名
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
        String objectName = "posts/" + user.getUserId() + "/" + IdUtil.simpleUUID() + ext;
        return Result.success(ossService.presign(objectName));
    }

    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}