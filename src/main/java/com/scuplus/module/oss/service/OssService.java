package com.scuplus.module.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListPartsRequest;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PartListing;
import com.amazonaws.services.s3.model.PartSummary;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.scuplus.common.config.MinioConfig;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.module.oss.dto.CompleteMultipartRequest;
import com.scuplus.module.oss.dto.MultipartInitDTO;
import com.scuplus.module.oss.dto.PartDTO;
import com.scuplus.module.oss.dto.PartPresignDTO;
import com.scuplus.module.oss.dto.PresignDTO;
import com.scuplus.module.oss.dto.PresignPartsDTO;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * OSS 服务
 *
 * 两套上传：
 *  1. 预签名直传（MinioClient）：小文件（图片），前端拿限时 PUT 链接直接传
 *  2. 分片上传（AmazonS3，S3 multipart）：大文件（视频），支持断点续传
 *
 * 为什么分片用 AmazonS3 而不是 MinioClient？
 *  MinIO 兼容 S3 协议，但 MinIO 自己的 SDK 只暴露 uploadObject（一段传完，无法手动控制分片）。
 * 手动分片/断点续传需要 S3 标准 multipart 接口（initiate/uploadPart/complete/listParts/abort），
 * 所以用 AmazonS3 连 MinIO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final MinioClient minioClient;
    private final AmazonS3 s3Client;
    private final MinioConfig minioConfig;
    private final StringRedisTemplate redis;

    /** Redis：uploadId → objectName 的映射（24h 内完成合并） */
    private static final String KEY_UPLOAD = "upload:%s:object";

    /** 建议分片大小：5MB（S3 要求每片 ≥5MB，最后一片可小） */
    private static final long CHUNK_SIZE = 5 * 1024 * 1024L;

    /** 启动时确保 bucket 存在（幂等），开发环境设为公开读，上传后前端可直接访问 */
    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("MinIO bucket '{}' 已创建", minioConfig.getBucket());
            }
            if (minioConfig.getPublicRead()) {
                String policy = """
                        {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                        """.formatted(minioConfig.getBucket());
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder().bucket(minioConfig.getBucket()).config(policy).build());
                log.info("MinIO bucket '{}' 已设为公开读", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败，请确认 MinIO 已启动", e);
        }
    }

    /**
     * 生成预签名上传链接（小文件：图片）
     */
    public PresignDTO presign(String objectName) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioConfig.getBucket())
                            .object(objectName)
                            .expiry(5, TimeUnit.MINUTES)
                            .build());

            String objectUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucket())
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build());

            PresignDTO dto = new PresignDTO();
            dto.setPresignedUrl(presignedUrl);
            dto.setObjectUrl(objectUrl);
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("生成上传链接失败", e);
        }
    }

    // ==================== 分片上传（大文件：视频，AmazonS3 multipart）====================

    /** ① 创建分片上传会话，返回 uploadId */
    public MultipartInitDTO initMultipart(String objectName) {
        try {
            InitiateMultipartUploadResult result = s3Client.initiateMultipartUpload(
                    new InitiateMultipartUploadRequest(minioConfig.getBucket(), objectName));
            String uploadId = result.getUploadId();
            // 记录 uploadId → objectName，24 小时内需完成合并
            redis.opsForValue().set(KEY_UPLOAD.formatted(uploadId), objectName, 24, TimeUnit.HOURS);

            MultipartInitDTO dto = new MultipartInitDTO();
            dto.setUploadId(uploadId);
            dto.setObjectName(objectName);
            dto.setChunkSize(CHUNK_SIZE);
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("初始化分片上传失败", e);
        }
    }

    /** ② 为每个分片生成预签名 PUT URL（前端直接 PUT 原始字节到 MinIO，文件不经后端） */
    public PresignPartsDTO presignParts(String uploadId, int total) {
        String objectName = redis.opsForValue().get(KEY_UPLOAD.formatted(uploadId));
        if (objectName == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片会话不存在或已过期，请重新 init");
        }
        List<PartPresignDTO> parts = new ArrayList<>();
        try {
            for (int i = 1; i <= total; i++) {
                String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .expiry(1, TimeUnit.HOURS)
                        // 这两个参数是 S3 multipart 上传分片的必要参数，会被签进 URL
                        .extraQueryParams(Map.of("partNumber", String.valueOf(i), "uploadId", uploadId))
                        .build());
                parts.add(new PartPresignDTO(i, url));
            }
        } catch (Exception e) {
            throw new RuntimeException("生成分片预签名链接失败", e);
        }
        PresignPartsDTO dto = new PresignPartsDTO();
        dto.setUploadId(uploadId);
        dto.setParts(parts);
        return dto;
    }

    /** ③ 合并所有分片，返回最终访问 URL（合并前校验完整性） */
    public String completeMultipart(CompleteMultipartRequest req) {
        String uploadId = req.getUploadId();
        String objectName = redis.opsForValue().get(KEY_UPLOAD.formatted(uploadId));
        if (objectName == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片会话不存在或已过期，请重新 init");
        }
        try {
            // ① 以 MinIO 实际已传分片为准，先查一遍（不是信前端说的）
            PartListing listing = s3Client.listParts(
                    new ListPartsRequest(minioConfig.getBucket(), objectName, uploadId));
            List<PartSummary> uploaded = listing.getParts();

            // 数量校验：前端声称的片数必须等于实际已传片数
            if (uploaded == null || uploaded.size() != req.getParts().size()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "分片数量不完整（实际 " + (uploaded == null ? 0 : uploaded.size())
                                + " / 声称 " + req.getParts().size() + "），请补传缺失分片");
            }

            // etag 校验：每个分片的 etag 必须和 MinIO 记录的一致（防伪造/防传错）
            Map<Integer, String> realEtags = new HashMap<>();
            for (PartSummary ps : uploaded) {
                realEtags.put(ps.getPartNumber(), ps.getETag());
            }
            for (PartDTO p : req.getParts()) {
                String real = realEtags.get(p.getPartNumber());
                if (real == null || !real.equalsIgnoreCase(p.getEtag())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "分片 " + p.getPartNumber() + " 校验失败，内容与上传时不一致，请重传该片");
                }
            }

            // ② 校验全部通过，才通知 MinIO 合并
            List<PartETag> partETags = req.getParts().stream()
                    .sorted(Comparator.comparing(PartDTO::getPartNumber))
                    .map(p -> new PartETag(p.getPartNumber(), p.getEtag()))
                    .collect(Collectors.toList());
            s3Client.completeMultipartUpload(new CompleteMultipartUploadRequest(
                    minioConfig.getBucket(), objectName, uploadId, partETags));
            redis.delete(KEY_UPLOAD.formatted(uploadId));

            // 返回 GET 签名 URL（7 天）
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .expiry(7, TimeUnit.DAYS)
                    .build());
        } catch (BusinessException e) {
            // 业务错误透传（返回友好错误码），不包成 RuntimeException
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("合并分片失败", e);
        }
    }

    /** 断点续传：查询已传分片号（前端据此只补缺失分片） */
    public List<Integer> listParts(String uploadId) {
        String objectName = redis.opsForValue().get(KEY_UPLOAD.formatted(uploadId));
        if (objectName == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片会话不存在或已过期，请重新 init");
        }
        List<Integer> partNumbers = new ArrayList<>();
        try {
            PartListing listing = s3Client.listParts(
                    new ListPartsRequest(minioConfig.getBucket(), objectName, uploadId));
            List<PartSummary> parts = listing.getParts();
            if (parts != null) {
                for (PartSummary part : parts) {
                    partNumbers.add(part.getPartNumber());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询已传分片失败", e);
        }
        return partNumbers;
    }

    /** 取消分片上传（释放 S3 资源 + 清理 Redis 映射） */
    public void abortMultipart(String uploadId) {
        String objectName = redis.opsForValue().get(KEY_UPLOAD.formatted(uploadId));
        if (objectName == null) {
            return;
        }
        try {
            s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(
                    minioConfig.getBucket(), objectName, uploadId));
        } catch (Exception e) {
            throw new RuntimeException("取消分片上传失败", e);
        }
        redis.delete(KEY_UPLOAD.formatted(uploadId));
    }
}