package com.danmaku.service;

import com.danmaku.vo.ResultVo;

import java.util.List;

public interface SessionService {
    ResultVo<?> createSingleSession(String accessToken, String targetUserId);

    ResultVo<?> createGroupSession(String accessToken, String groupName, List<String> memberIds);

    ResultVo<?> getSessionList(String accessToken, Integer pageNum, Integer pageSize);
}
