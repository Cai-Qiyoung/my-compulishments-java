package com.danmaku.mapper;

import com.danmaku.vo.ContactVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContactMapper {
    long countContacts(@Param("userId") String userId);

    List<ContactVO> selectContacts(@Param("userId") String userId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countBlockedContacts(@Param("userId") String userId);

    List<ContactVO> selectBlockedContacts(@Param("userId") String userId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);
}
