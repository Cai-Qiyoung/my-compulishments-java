package com.danmaku.controller;

import com.danmaku.service.RelationService;
import com.danmaku.vo.ResultVo;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/relation")
public class RelationController {
    @Resource
    private RelationService relationService;

    /**
     * 关注/取关用户
     */
    @PostMapping("/follow")
    public ResultVo<?> followUser(@RequestParam String to_user_id,
                                  @RequestHeader("Access-Token")  String accessToken) {
        return relationService.followUser(accessToken, to_user_id );
    }

    /**
     * 获取关注列表
     */
    @RequestMapping("/follow/list")
    public ResultVo<?> getFollowList(@RequestParam(required = false) String user_id,
                                     @RequestHeader(required = false, name = "Access-Token") String accessToken,
                                     @RequestParam(defaultValue = "1") Integer page_num,
                                     @RequestParam(defaultValue = "10") Integer page_size) {
        return relationService.getFollowList(user_id,accessToken , page_num, page_size);
    }

    /**
     * 获取粉丝列表
     */
    @RequestMapping("/fans/list")
    public ResultVo<?> getFansList(@RequestParam(required = false) String user_id,
                                   @RequestHeader(required = false, name = "Access-Token")  String accessToken,
                                   @RequestParam(defaultValue = "1") Integer page_num,
                                   @RequestParam(defaultValue = "10") Integer page_size) {
        return relationService.getFansList(user_id,accessToken, page_num, page_size);
    }

    @GetMapping("/friends/list")
    public ResultVo<?> getFriendList(
            @RequestHeader(name = "Access_Token") String accessToken,
            @RequestParam(defaultValue = "0") Integer page_num,
            @RequestParam(defaultValue = "10") Integer page_size) {

        return relationService.getFriendList(accessToken, page_num, page_size);
    }
}