package com.danmaku.service.impl;

import cn.hutool.core.util.IdUtil;
import com.danmaku.constant.RedisKeys;
import com.danmaku.entity.ContactBlock;
import com.danmaku.mapper.ContactBlockMapper;
import com.danmaku.mapper.ContactMapper;
import com.danmaku.mapper.UserMapper;
import com.danmaku.service.ContactService;
import com.danmaku.util.RedisUtil;
import com.danmaku.vo.ContactVO;
import com.danmaku.vo.ResultVo;
import jakarta.annotation.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ContactServiceImpl implements ContactService {
    private static final long CONTACT_CACHE_TTL_MINUTES = 5L;
    private static final long CONTACT_LOCK_TTL_SECONDS = 5L;

    @Resource
    private ContactMapper contactMapper;
    @Resource
    private ContactBlockMapper contactBlockMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private RedisUtil redisUtil;

    @Override
    public ResultVo<?> getContactList(String accessToken, Integer pageNum, Integer pageSize) {
        String userId = currentUserId();
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        String cacheKey = RedisKeys.contactList(userId, safePageNum, safePageSize);
        Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
        if (cached != null) {
            return ResultVo.success(cached);
        }

        long total = contactMapper.countContacts(userId);
        List<ContactVO> items = total == 0
                ? List.of()
                : contactMapper.selectContacts(userId, calcOffset(safePageNum, safePageSize), safePageSize);

        Map<String, Object> data = buildPage(items, total, safePageNum, safePageSize);
        redisUtil.setCacheObjectSafely(cacheKey, data, CONTACT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(data);
    }

    @Override
    public ResultVo<?> getBlockedList(String accessToken, Integer pageNum, Integer pageSize) {
        String userId = currentUserId();
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        String cacheKey = RedisKeys.contactList(userId, -safePageNum, safePageSize);
        Map<String, Object> cached = redisUtil.getCacheObjectSafely(cacheKey);
        if (cached != null) {
            return ResultVo.success(cached);
        }

        long total = contactMapper.countBlockedContacts(userId);
        List<ContactVO> items = total == 0
                ? List.of()
                : contactMapper.selectBlockedContacts(userId, calcOffset(safePageNum, safePageSize), safePageSize);

        Map<String, Object> data = buildPage(items, total, safePageNum, safePageSize);
        redisUtil.setCacheObjectSafely(cacheKey, data, CONTACT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return ResultVo.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> blockContact(String accessToken, String targetUserId) {
        return updateBlockStatus(targetUserId, 0, "屏蔽成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVo<?> unblockContact(String accessToken, String targetUserId) {
        return updateBlockStatus(targetUserId, 1, "取消屏蔽成功");
    }

    private ResultVo<?> updateBlockStatus(String targetUserId, int status, String successMsg) {
        String userId = currentUserId();
        if (userId.equals(targetUserId)) {
            return ResultVo.fail("不能操作自己");
        }
        if (userMapper.selectById(targetUserId) == null) {
            return ResultVo.fail("目标用户不存在");
        }

        String lockKey = RedisKeys.contactBlockLock(userId, targetUserId);
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(lockKey, lockValue, CONTACT_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            return ResultVo.fail("操作过于频繁，请稍后重试");
        }

        try {
            ContactBlock block = contactBlockMapper.selectByUsers(userId, targetUserId);
            if (block == null) {
                if (status != 0) {
                    return ResultVo.success(successMsg);
                }
                ContactBlock newBlock = new ContactBlock();
                newBlock.setId(IdUtil.getSnowflakeNextIdStr());
                newBlock.setBlockerUserId(userId);
                newBlock.setBlockedUserId(targetUserId);
                newBlock.setStatus(0);
                newBlock.setCreatedAt(LocalDateTime.now());
                newBlock.setUpdatedAt(LocalDateTime.now());
                contactBlockMapper.insert(newBlock);
            } else if (block.getStatus() != status) {
                contactBlockMapper.updateStatusById(block.getId(), status);
            }

            invalidateUserCaches(userId, targetUserId);
            return ResultVo.success(successMsg);
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    private Map<String, Object> buildPage(List<ContactVO> items, long total, int pageNum, int pageSize) {
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("current", pageNum);
        data.put("size", pageSize);
        data.put("pages", calcPages(total, pageSize));
        return data;
    }

    private void invalidateUserCaches(String userId, String targetUserId) {
        redisUtil.deleteByPrefix(RedisKeys.contactListPrefix(userId));
        redisUtil.deleteByPrefix(RedisKeys.friendListPrefix(userId));
        redisUtil.deleteByPrefix(RedisKeys.friendListPrefix(targetUserId));
        redisUtil.deleteByPrefix(RedisKeys.sessionListPrefix(userId));
        redisUtil.deleteByPrefix(RedisKeys.sessionListPrefix(targetUserId));
    }

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
    }

    private int calcOffset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

    private long calcPages(long total, int pageSize) {
        return pageSize <= 0 ? 0L : (total + pageSize - 1) / pageSize;
    }
}
