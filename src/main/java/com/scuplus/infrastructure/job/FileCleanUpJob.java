package com.scuplus.infrastructure.job;

import com.scuplus.common.config.MinioConfig;
import com.scuplus.module.share.mapper.PostMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor

public class FileCleanUpJob {
    private final PostMapper postMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanFiles(){
        log.info("开始扫描并清理孤儿图片......");
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .prefix("posts/")  // 只扫帖子图片目录
                            .recursive(true)   // 递归子目录
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName(); // 比如 "posts/1/123.jpg"

                // 2. 检查数据库中是否有帖子引用了这个路径
                // 注意：你的 media_urls 存的是完整 URL，我们用 LIKE 模糊匹配路径
                int count = postMapper.countByMediaUrl("%" + objectName + "%");

                if (count == 0) {
                    // 3. 没有任何帖子引用 → 物理删除
                    minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                    .bucket(minioConfig.getBucket())
                                    .object(objectName)
                                    .build()
                    );
                    log.info("已删除孤儿文件: {}", objectName);
                }
            }
            log.info("孤儿文件清理完成");

        }catch (Exception e){
            log.info("孤儿文件清理失败");
        }
    }

}
