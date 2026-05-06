package com.danmaku.controller;

import com.danmaku.service.ContactService;
import com.danmaku.vo.ResultVo;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contact")
public class ContactController {
    @Resource
    private ContactService contactService;

    @GetMapping("/list")
    public ResultVo<?> getContactList(@RequestHeader("Access-Token") String accessToken,
                                      @RequestParam(defaultValue = "1") Integer page_num,
                                      @RequestParam(defaultValue = "10") Integer page_size) {
        return contactService.getContactList(accessToken, page_num, page_size);
    }

    @GetMapping("/blocked/list")
    public ResultVo<?> getBlockedList(@RequestHeader("Access-Token") String accessToken,
                                      @RequestParam(defaultValue = "1") Integer page_num,
                                      @RequestParam(defaultValue = "10") Integer page_size) {
        return contactService.getBlockedList(accessToken, page_num, page_size);
    }

    @PostMapping("/block")
    public ResultVo<?> blockContact(@RequestHeader("Access-Token") String accessToken,
                                    @RequestParam("target_user_id") String targetUserId) {
        return contactService.blockContact(accessToken, targetUserId);
    }

    @PostMapping("/unblock")
    public ResultVo<?> unblockContact(@RequestHeader("Access-Token") String accessToken,
                                      @RequestParam("target_user_id") String targetUserId) {
        return contactService.unblockContact(accessToken, targetUserId);
    }
}
