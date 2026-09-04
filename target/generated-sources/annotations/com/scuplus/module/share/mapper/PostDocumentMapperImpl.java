package com.scuplus.module.share.mapper;

import com.scuplus.module.share.entity.Post;
import com.scuplus.module.share.entity.PostDocument;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-04T17:04:45+0800",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class PostDocumentMapperImpl implements PostDocumentMapper {

    @Override
    public PostDocument toDocument(Post post) {
        if ( post == null ) {
            return null;
        }

        PostDocument postDocument = new PostDocument();

        postDocument.setUserid( post.getUserId() );
        postDocument.setId( post.getId() );
        postDocument.setContent( post.getContent() );
        postDocument.setStatus( post.getStatus() );
        postDocument.setCreatedAt( post.getCreatedAt() );

        return postDocument;
    }
}
