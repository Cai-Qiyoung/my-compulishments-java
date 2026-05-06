package com.danmaku.controller;

import com.danmaku.service.SessionService;
import com.danmaku.vo.ResultVo;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/session")
public class SessionController {
    @Resource
    private SessionService sessionService;

    @PostMapping("/single")
    public ResultVo<?> createSingleSession(@RequestHeader("Access-Token") String accessToken,
                                           @RequestParam("target_user_id") String targetUserId) {
        return sessionService.createSingleSession(accessToken, targetUserId);
    }

    @PostMapping("/group")
    public ResultVo<?> createGroupSession(@RequestHeader("Access-Token") String accessToken,
                                          @RequestParam(value = "group_name", required = false) String groupName,
                                          @RequestParam("member_ids") List<String> memberIds) {
        return sessionService.createGroupSession(accessToken, groupName, memberIds);
    }

    @GetMapping("/list")
    public ResultVo<?> getSessionList(@RequestHeader("Access-Token") String accessToken,
                                      @RequestParam(defaultValue = "1") Integer page_num,
                                      @RequestParam(defaultValue = "10") Integer page_size) {
        return sessionService.getSessionList(accessToken, page_num, page_size);
    }
}
