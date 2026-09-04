package com.scuplus.module.share.mapper;
import com.scuplus.module.share.entity.Post;
import com.scuplus.module.share.entity.PostDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostDocumentMapper {
    @Mapping(target = "userid",source = "userId")
    PostDocument toDocument(Post post);
}
