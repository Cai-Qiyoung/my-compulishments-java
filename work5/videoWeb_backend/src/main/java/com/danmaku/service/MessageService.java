package com.danmaku.service;

import com.danmaku.vo.ChatMessageVO;
import com.danmaku.vo.ResultVo;

public interface MessageService {
    ResultVo<?> sendMessage(String accessToken, String conversationId, String messageType, String content);

    ChatMessageVO sendMessageByUserId(String userId, String conversationId, String messageType, String content);

    ResultVo<?> getHistory(String accessToken, String conversationId, String startTime, String endTime, Integer pageNum, Integer pageSize);
}
