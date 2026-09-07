package com.scuplus.module.share.service;

import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.module.share.entity.PostDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostSearchService {

    private final ElasticsearchTemplate template;

    /** 索引是否已就绪——避免每次搜索都去查 indexOps.exists */
    private volatile boolean indexReady = false;

    @PostConstruct
    public void init() {
        // 首次启动就尝试建索引；失败不阻塞应用（ES 可能稍后起）
        ensureIndex();
    }

    /** 确保 posts 索引存在（幂等）。ES 挂时静默失败，等搜索/保存时再试。 */
    private void ensureIndex() {
        if (indexReady) {
            return;
        }
        try {
            IndexOperations ops = template.indexOps(PostDocument.class);
            if (!ops.exists()) {
                ops.create();
                ops.putMapping();
            }
            indexReady = true;
            log.info("ES posts 索引已就绪(IK分词)");
        } catch (Exception e) {
            // ES 还没起：不阻塞应用，等下次 search/save 再试
            log.warn("ES 索引暂时不可用(请确认 ES 已启动 127.0.0.1:9200): {}", e.getMessage());
        }
    }

    public void save(PostDocument doc) {
        ensureIndex();
        try {
            template.save(doc);
        } catch (Exception e) {
            // ES 故障不能把发帖主流程拖死——MySQL 已落库，ES 稍后可补
            log.error("帖子同步 ES 失败(MySQL 已保存,不影响发帖): docId={}", doc.getId(), e);
        }
    }

    public List<PostDocument> search(String key, int page, int size) {
        ensureIndex();
        if (page < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "page 从 1 开始");
        }
        // 只搜未删除：status=0；防止已删帖被搜出来
        Criteria criteria = new Criteria("status").is(0)
                .and(new Criteria("content").matches(key));
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(page - 1, size));
        SearchHits<PostDocument> hits = template.search(query, PostDocument.class);
        return hits.getSearchHits().stream()
                .map(SearchHit::getContent).toList();
    }
}