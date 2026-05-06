package com.danmaku.controller;

import com.danmaku.service.MessageService;
import com.danmaku.vo.ResultVo;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/message")
public class MessageController {
    @Resource
    private MessageService messageService;

    @PostMapping("/send")
    public ResultVo<?> sendMessage(@RequestHeader("Access-Token") String accessToken,
                                   @RequestParam("conversation_id") String conversationId,
                                   @RequestParam("message_type") String messageType,
                                   @RequestParam String content) {
        return messageService.sendMessage(accessToken, conversationId, messageType, content);
    }

    @GetMapping("/history")
    public ResultVo<?> getHistory(@RequestHeader("Access-Token") String accessToken,
                                  @RequestParam("conversation_id") String conversationId,
                                  @RequestParam(value = "start_time", required = false) String startTime,
                                  @RequestParam(value = "end_time", required = false) String endTime,
                                  @RequestParam(defaultValue = "1") Integer page_num,
                                  @RequestParam(defaultValue = "20") Integer page_size) {
        return messageService.getHistory(accessToken, conversationId, startTime, endTime, page_num, page_size);
    }
}
