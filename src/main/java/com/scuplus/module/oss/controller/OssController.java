package com.scuplus.module.oss.controller;

import cn.hutool.core.util.IdUtil;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.oss.dto.AbortRequest;
import com.scuplus.module.oss.dto.CompleteMultipartRequest;
import com.scuplus.module.oss.dto.MultipartInitDTO;
import com.scuplus.module.oss.dto.PresignDTO;
import com.scuplus.module.oss.dto.PresignPartsDTO;
import com.scuplus.module.oss.dto.PresignPartsRequest;
import com.scuplus.module.oss.service.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OSS 接口（需登录）
 *
 * 小文件（图片）：POST /presign → 前端直传
 * 大文件（视频）分片：
 *   POST /multipart/init          → 建会话，返回 uploadId
 *   POST /multipart/upload        → 传一片（multipart/form-data）
 *   GET  /multipart/parts         → 查已传分片（断点续传）
 *   POST /multipart/complete      → 合并所有分片
 *   POST /multipart/abort         → 取消
 */
@RestController
@RequestMapping("/api/v1/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssService ossService;

    /** 获取上传链接（小文件直传） */
    @PostMapping("/presign")
    public Result<PresignDTO> presign(@RequestParam String fileName) {
        LoginUser user = currentUser();
        String ext = extOf(fileName);
        String objectName = "posts/" + user.getUserId() + "/" + IdUtil.simpleUUID() + ext;
        return Result.success(ossService.presign(objectName));
    }

    /** ① 初始化分片上传（大视频） */
    @PostMapping("/multipart/init")
    public Result<MultipartInitDTO> initMultipart(@RequestParam String fileName) {
        LoginUser user = currentUser();
        String ext = extOf(fileName);
        String objectName = "videos/" + user.getUserId() + "/" + IdUtil.simpleUUID() + ext;
        return Result.success(ossService.initMultipart(objectName));
    }

    /** ② 为每个分片生成预签名 PUT URL（前端直接 PUT 原始字节到 MinIO，文件不经后端） */
    @PostMapping("/multipart/presign-parts")
    public Result<PresignPartsDTO> presignParts(@RequestBody PresignPartsRequest request) {
        return Result.success(ossService.presignParts(request.getUploadId(), request.getTotal()));
    }

    /** ③ 查询已传分片号（断点续传：前端据此只补缺失分片） */
    @GetMapping("/multipart/parts")
    public Result<List<Integer>> parts(@RequestParam String uploadId) {
        return Result.success(ossService.listParts(uploadId));
    }

    /** ④ 合并所有分片，返回最终访问 URL */
    @PostMapping("/multipart/complete")
    public Result<String> complete(@RequestBody CompleteMultipartRequest request) {
        return Result.success(ossService.completeMultipart(request));
    }

    /** ⑤ 取消分片上传 */
    @PostMapping("/multipart/abort")
    public Result<Void> abort(@RequestBody AbortRequest request) {
        ossService.abortMultipart(request.getUploadId());
        return Result.success();
    }

    /** 从文件名提取扩展名（如 "a.mp4" → ".mp4"；无点则空串） */
    private String extOf(String fileName) {
        return fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
    }

    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}