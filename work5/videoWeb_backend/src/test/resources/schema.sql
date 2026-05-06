DROP TABLE IF EXISTS `like_record`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `conversation_member`;
DROP TABLE IF EXISTS `conversation`;
DROP TABLE IF EXISTS `contact_block`;
DROP TABLE IF EXISTS `relation`;
DROP TABLE IF EXISTS `comment`;
DROP TABLE IF EXISTS `video`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` varchar(64) NOT NULL,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `role` varchar(32) NOT NULL DEFAULT 'USER',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` timestamp DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`username`)
);

CREATE TABLE `video` (
  `id` varchar(64) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `video_url` varchar(255) NOT NULL,
  `cover_url` varchar(255) NOT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `visit_count` int DEFAULT 0,
  `like_count` int DEFAULT 0,
  `comment_count` int DEFAULT 0,
  `audit_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `audit_reason` varchar(255) DEFAULT NULL,
  `audit_by` varchar(64) DEFAULT NULL,
  `audit_at` timestamp DEFAULT NULL,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` timestamp DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `comment` (
  `id` varchar(64) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `video_id` varchar(64) NOT NULL,
  `parent_id` varchar(64) NOT NULL DEFAULT '0',
  `like_count` int DEFAULT 0,
  `child_count` int DEFAULT 0,
  `content` varchar(500) NOT NULL,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` timestamp DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `relation` (
  `id` varchar(64) NOT NULL,
  `from_user_id` varchar(64) NOT NULL,
  `to_user_id` varchar(64) NOT NULL,
  `status` tinyint DEFAULT 0,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`from_user_id`, `to_user_id`)
);

CREATE TABLE `like_record` (
  `id` varchar(64) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `video_id` varchar(64) DEFAULT NULL,
  `comment_id` varchar(64) DEFAULT NULL,
  `type` tinyint NOT NULL,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE `contact_block` (
  `id` varchar(64) NOT NULL,
  `blocker_user_id` varchar(64) NOT NULL,
  `blocked_user_id` varchar(64) NOT NULL,
  `status` tinyint DEFAULT 0,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`blocker_user_id`, `blocked_user_id`)
);

CREATE TABLE `conversation` (
  `id` varchar(64) NOT NULL,
  `conversation_type` varchar(16) NOT NULL,
  `biz_key` varchar(128) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `creator_id` varchar(64) NOT NULL,
  `last_message` varchar(500) DEFAULT NULL,
  `last_message_type` varchar(16) DEFAULT NULL,
  `last_message_time` timestamp DEFAULT NULL,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` timestamp DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `conversation_member` (
  `id` varchar(64) NOT NULL,
  `conversation_id` varchar(64) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `member_role` varchar(16) NOT NULL DEFAULT 'MEMBER',
  `status` tinyint DEFAULT 0,
  `joined_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`conversation_id`, `user_id`)
);

CREATE TABLE `chat_message` (
  `id` varchar(64) NOT NULL,
  `conversation_id` varchar(64) NOT NULL,
  `sender_id` varchar(64) NOT NULL,
  `message_type` varchar(16) NOT NULL,
  `content` varchar(1000) NOT NULL,
  `sent_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);
