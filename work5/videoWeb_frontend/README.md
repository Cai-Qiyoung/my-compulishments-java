# videoWeb Frontend

这是一个根据 `videoWeb.openapi.json` 搭建的前端项目，适配以下后端接口能力：

- 用户注册、登录、头像上传、当前用户信息
- 视频列表、热门榜、搜索、投稿
- 评论列表、发布评论、删除评论、点赞评论
- 点赞视频、点赞列表
- 关注操作、关注列表、粉丝列表、好友列表

## 本地运行

1. 安装依赖

```bash
npm install
```

2. 配置接口地址

```bash
cp .env.example .env
```

将 `.env` 里的 `VITE_API_BASE_URL` 改成你的后端地址，例如：

```bash
VITE_API_BASE_URL=https://api.your-domain.com
```

3. 启动开发环境

```bash
npm run dev
```

## 生产构建

```bash
npm run build
```

构建产物会输出到 `dist/`。

## 云服务器部署

### 方式 1：直接部署静态文件

将 `dist/` 目录上传到 Nginx 静态站点目录，然后使用 `deploy/nginx.conf` 里的配置托管即可。

### 方式 2：Docker 部署

```bash
docker build -t video-web-frontend .
docker run -d -p 80:80 video-web-frontend
```

## 注意事项

- 前端默认会把 `Access-Token` 和 `Refresh-Token` 放到请求头中。
- 由于接口文档中没有单独的视频详情接口，当前详情弹窗使用列表接口返回的数据作为详情基础数据，再额外请求评论。
- 由于接口文档里没有明确的“是否已点赞/是否已关注”状态字段，当前界面提供操作按钮，但不会在初始化时显示精确的已点赞/已关注态。
