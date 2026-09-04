package com.scuplus.module.share.entity;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "posts")
public class PostDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Keyword)
    private Long userid;


    @Field(type = FieldType.Text,analyzer = "ik_max_word",searchAnalyzer = "ik_max_word")
    private String content;

    @Field(type = FieldType.Keyword)
    private Integer status;

    @Field(type = FieldType.Date,format = DateFormat.date_time)
    private LocalDateTime createdAt;
}
