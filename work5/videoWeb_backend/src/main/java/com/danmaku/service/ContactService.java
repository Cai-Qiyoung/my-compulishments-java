package com.danmaku.service;

import com.danmaku.vo.ResultVo;

public interface ContactService {
    ResultVo<?> getContactList(String accessToken, Integer pageNum, Integer pageSize);

    ResultVo<?> getBlockedList(String accessToken, Integer pageNum, Integer pageSize);

    ResultVo<?> blockContact(String accessToken, String targetUserId);

    ResultVo<?> unblockContact(String accessToken, String targetUserId);
}
