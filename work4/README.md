# danmakuVideoWebApi

## 项目简介

本项目是一个基于 Spring Boot + MyBatis-Plus 构建的视频网站后端服务，核心实现了用户管理、视频投稿与热门排行、评论互动、点赞、用户关系（关注 / 粉丝 / 好友）等核心功能，提供完整的 RESTful API 接口，支持用户认证、分页查询、事务管理等企业级特性。

## 技术栈

### 核心框架与工具

- **基础框架**：Spring Boot
- **ORM 框架**：MyBatis-Plus（简化 CRUD 操作、分页、条件查询）
- **认证授权**：JWT（生成 Access Token/Refresh Token，解析用户身份）
- **事务管理**：Spring Transactional（声明式事务，支持回滚）
- **分页插件**：MyBatis-Plus Pagination（分页查询）
- **安全加密**：Spring Security PasswordEncoder（密码加密）
- **缓存**：Redis（热门视频排行、点赞数 / 访问量缓存）
- **文件处理**：自定义文件上传工具（视频、封面、头像上传）
- **数据库**：MySQL（实体映射、关系存储）

## 核心接口介绍

### 1. 用户模块（UserService）

| 接口           | 功能            | 参数说明                                                     |
| :------------- | :-------------- | :----------------------------------------------------------- |
| `register`     | 用户注册        | username（用户名）、password（密码）                         |
| `login`        | 用户登录        | username、password，返回双令牌（access_token/refresh_token）+ 用户信息 |
| `getUserInfo`  | 获取用户信息    | accessToken（身份令牌）                                      |
| `uploadAvatar` | 上传 / 修改头像 | accessToken、MultipartFile（头像文件）                       |

### 2. 视频模块（VideoService）

| 接口              | 功能                             | 参数说明                                        |
| :---------------- | :------------------------------- | :---------------------------------------------- |
| `publishVideo`    | 异步投稿视频                     | accessToken、视频文件、封面文件、标题、描述     |
| `getVideoList`    | 获取用户发布的视频列表           | accessToken、user_id（可选）、pageNum、pageSize |
| `getPopularVideo` | 获取热门视频排行榜（Redis 缓存） | pageNum、pageSize                               |
| `searchVideo`     | 搜索视频                         | keywords（关键词）、pageNum、pageSize           |

### 3. 评论模块（CommentService）

| 接口             | 功能             | 参数说明                                                     |
| :--------------- | :--------------- | :----------------------------------------------------------- |
| `publishComment` | 发布评论 / 回复  | accessToken、videoId、content（内容）、parentId（父评论 ID，0 为根评论） |
| `getCommentList` | 获取视频评论列表 | videoId、pageNum、pageSize                                   |
| `deleteComment`  | 删除评论         | accessToken、commentId（评论 ID）                            |

### 4. 点赞模块（LikeService）

| 接口          | 功能                | 参数说明                                          |
| :------------ | :------------------ | :------------------------------------------------ |
| `likeVideo`   | 点赞 / 取消点赞视频 | accessToken、videoId                              |
| `likeComment` | 点赞 / 取消点赞评论 | accessToken、commentId                            |
| `likeList`    | 获取用户点赞列表    | accessToken、user_id（可选）、page_num、page_size |

### 5. 用户关系模块（RelationService）

| 接口            | 功能                     | 参数说明                                       |
| :-------------- | :----------------------- | :--------------------------------------------- |
| `followUser`    | 关注 / 取关用户          | accessToken、toUserId（被关注者 ID）           |
| `getFollowList` | 获取用户关注列表         | userId（可选）、accessToken、pageNum、pageSize |
| `getFansList`   | 获取用户粉丝列表         | userId（可选）、accessToken、pageNum、pageSize |
| `getFriendList` | 获取好友列表（互相关注） | accessToken、pageNum、pageSize                 |

## 代码实现核心特点

### 1. 统一的返回结果封装

所有接口返回`ResultVo`对象，统一格式：

```
// 成功响应
ResultVo.success("操作成功");
ResultVo.success(data); // 带数据返回

// 失败响应
ResultVo.fail("操作失败");
```

### 2. JWT 身份认证

核心工具类`JwtUtil`解析 token 获取用户 ID，所有需要权限的接口均通过`accessToken`校验身份：

```
Long userId = jwtUtil.getUserIdFromToken(accessToken);
```

### 3. 事务管理

核心操作（如点赞、评论、关注）均添加`@Transactional(rollbackFor = Exception.class)`保证事务一致性：

```
@Override
@Transactional(rollbackFor = Exception.class)
public ResultVo<?> likeVideo(String accessToken, String videoId) {
    // 点赞/取消点赞逻辑 + 视频点赞数更新
}
```

### 4. 分页查询实现

基于 MyBatis-Plus `Page`对象实现分页，示例（评论列表）：

```
Page<Comment> page = new Page<>(pageNum, pageSize);
LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Comment::getVideoId, videoId)
        .eq(Comment::getParentId, "0")
        .orderByDesc(Comment::getCreatedAt);
IPage<Comment> iPage = page(page, wrapper);
return ResultVo.success(iPage);
```

### 5. Redis 缓存热门视频

热门视频排行基于 Redis ZSet 实现，访问量 / 点赞数变更时更新 ZSet 分数：

```
// 视频访问量+1，同步更新Redis热门榜分数
redisUtil.getRedisTemplate().opsForZSet().incrementScore(REDIS_POPULAR, videoId, 1D);

// 查询热门视频
Set<ZSetOperations.TypedTuple<Object>> tuples =
        redisUtil.getRedisTemplate().opsForZSet().reverseRangeWithScores(REDIS_POPULAR, start, end);
```

### 6. 数据校验与边界处理

所有修改 / 删除操作均做权限校验 + 数据边界处理，示例（删除评论）：

```
// 权限校验
if (!comment.getUserId().equals(String.valueOf(userId))) {
    return ResultVo.fail("无权限删除");
}

// 边界值处理（避免负数）
video.setCommentCount(Math.max(0, video.getCommentCount() - 1));
```

## 核心业务流程示例

### 1. 视频点赞 / 取消点赞流程

1. **身份校验**：通过`accessToken`调用`JwtUtil.getUserIdFromToken()`解析出当前用户 ID；
2. **查询点赞状态**：构造 Lambda 查询条件，查询该用户是否已点赞该视频（type=1 标记视频点赞）；
3. **点赞逻辑**：若未点赞，创建新的点赞记录并保存，同时将视频的点赞数 + 1；
4. **取消点赞逻辑**：若已点赞，删除该点赞记录，同时将视频点赞数 - 1（通过`Math.max(0, 数值)`保证点赞数不小于 0）；
5. **事务保障**：整个操作添加`@Transactional`注解，确保点赞记录和视频点赞数的修改原子性；
6. **结果返回**：返回 “点赞成功” 或 “取消点赞成功” 的统一 ResultVo 响应。

### 2. 视频投稿流程

1. **身份校验**：解析`accessToken`获取当前投稿用户 ID；
2. **文件上传**：调用自定义`FileUploadUtil`分别上传视频文件和封面文件，返回文件访问 URL；
3. **视频信息入库**：封装视频标题、描述、用户 ID、视频 URL、封面 URL 等信息，插入 Video 表；
4. **热门榜初始化**：向 Redis 的 ZSet 集合（danmaku:video:popular）中添加该视频 ID，初始分数为 0（分数对应热门权重）；
5. **异常处理**：捕获文件上传、数据库插入过程中的异常，抛出运行时异常并返回投稿失败响应；
6. **结果返回**：投稿成功则返回 “发布成功！” 的统一响应。

### 3. 发布评论 / 回复流程

1. **身份校验**：解析`accessToken`获取评论用户 ID；

2. **评论数据封装**：创建 Comment 对象，设置用户 ID、视频 ID、评论内容、父评论 ID（根评论为 0，子评论为父评论 ID）；

3. **评论入库**：保存评论记录到数据库；

4. **关联数据更新**：

   - 根评论：更新对应视频的评论数 + 1；
   - 子评论：除更新视频评论数外，额外更新父评论的子评论数 + 1；

   

5. **事务保障**：添加`@Transactional`注解，确保评论入库和关联数更新同时成功 / 失败；

6. **结果返回**：返回 “评论成功” 的统一响应。

### 4. 关注 / 取关用户流程

1. **合法性校验**：解析`accessToken`获取当前用户 ID，校验是否尝试关注自己，若则直接返回失败；
2. **关注关系查询**：构造 Lambda 查询条件，查询当前用户与目标用户是否已存在关注关系；
3. **关注逻辑**：若不存在关系，创建 Relation 记录，设置 status=0（已关注）并保存；
4. **取关逻辑**：若已存在关系，切换 status 状态（0→1 表示取关，1→0 表示重新关注）并更新；
5. **事务保障**：整个操作添加事务注解，确保关注关系的修改原子性；
6. **结果返回**：根据最终状态返回 “关注成功” 或 “已取关” 的统一响应。

### 5. 删除评论流程

1. **身份校验**：解析`accessToken`获取当前用户 ID；

2. **评论合法性校验**：查询评论 ID 对应的评论记录，若不存在则返回 “评论不存在”；

3. **权限校验**：校验当前用户是否为评论发布者，非发布者返回 “无权限删除”；

4. **删除逻辑**：

   - 根评论：删除该评论后，查询并删除所有子评论，同时将视频评论数扣除根评论 + 子评论的总数；
   - 子评论：删除该评论后，更新父评论的子评论数 - 1，同时将视频评论数 - 1；

   

5. **边界处理**：所有数量更新均通过`Math.max(0, 数值)`保证不出现负数；

6. **事务保障**：添加事务注解，确保评论删除和关联数更新的原子性；

7. **结果返回**：返回 “删除成功” 的统一响应。

### 6. 热门视频查询流程

1. **分页参数计算**：根据传入的 pageNum 和 pageSize，计算 Redis ZSet 的查询起始 / 结束下标（start = (pageNum-1)*pageSize，end = start + pageSize -1）；
2. **Redis 热门榜查询**：从 ZSet 集合中按分数倒序查询指定下标的视频 ID 及对应分数；
3. **视频信息查询**：根据查询到的视频 ID 列表，批量查询 Video 表获取视频详情；
4. **排序处理**：将查询到的视频列表按 Redis 中的分数倒序重新排序（保证热门顺序）；
5. **结果封装**：封装视频列表、总数等分页信息到 Map 中；
6. **空值处理**：若 Redis 中无数据，返回空 Map 的成功响应；
7. **结果返回**：返回包含热门视频列表的统一 ResultVo 响应。

### 7. 获取用户点赞列表流程

1. **目标用户确定**：

   - 若传入 user_id，以该 ID 为目标用户（查看他人点赞列表）；
   - 若未传入 user_id，解析 accessToken 获取当前用户 ID（查看自己点赞列表）；
   - 未登录且未传 user_id，返回 “未登录且未指定用户 ID” 的失败响应；

   

2. **分页查询点赞记录**：构造 Lambda 查询条件，分页查询该用户的所有点赞记录；

3. **关联视频信息**：遍历点赞记录，根据视频 ID 查询对应的视频详情，并封装到点赞记录对象中；

4. **结果封装**：将点赞记录列表、总条数封装到 Map 中；

5. **异常处理**：捕获查询过程中的异常，打印堆栈并返回 “获取喜欢列表失败” 的响应；

6. **结果返回**：返回包含点赞列表的统一响应。

## 扩展建议

1. 新增弹幕功能模块，基于 Redis / 消息队列实现实时弹幕推送；
2. 完善视频播放量统计，增加防刷机制；
3. 新增视频分类、标签功能；
4. 优化热门视频算法，结合点赞、评论、播放量多维度评分；
5. 增加接口限流、日志记录、异常统一处理。