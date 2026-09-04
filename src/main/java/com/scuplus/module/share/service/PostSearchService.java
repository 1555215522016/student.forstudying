package com.scuplus.module.search.service;

import com.scuplus.module.share.entity.PostDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.sql.SqlResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostSearchService {

    private final ElasticsearchTemplate template;

    @PostConstruct
    public void ensureIndex() {
        try {
            IndexOperations ops = template.indexOps(PostDocument.class);
            if (!ops.exists()) {
                ops.create();
                ops.putMapping();
                log.info("ES posts 索引已创建(IK分词)");
            } else {
                log.info("ES posts 索引已存在,跳过创建");
            }
        } catch (Exception e) {
            // ES 没起来也不让应用崩,只记日志;等 ES 起来后手动触发
            log.warn("ES 索引初始化失败(请确认 ES 已启动): {}", e.getMessage());
        }
    }

    public void save(PostDocument doc){
        template.save(doc);
    }

    public List<PostDocument> search(String key,int page,int size){
        Criteria criteria=new Criteria("content").matches(key);
        CriteriaQuery query=new CriteriaQuery(criteria);
        query.setPageable(org.springframework.data.domain.PageRequest.of(page - 1, size));
        SearchHits<PostDocument> hits=template.search(query, PostDocument.class);
        return hits.getSearchHits().stream()
                .map(SearchHit::getContent).toList();
    }
}
