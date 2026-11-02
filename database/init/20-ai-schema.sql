-- ai-service (ddl-auto: none) 테이블. 기준: AiGuideEntity
-- user_id, blog_post_id 는 다른 서비스 소유라 FK 없이 ID만 저장한다.
USE ai_service;

CREATE TABLE IF NOT EXISTS `ai_guide` (
  `ai_guide_id`     bigint(20)  NOT NULL AUTO_INCREMENT,
  `user_id`         bigint(20)  NOT NULL,
  `blog_post_id`    bigint(20)  NOT NULL,
  `profile_version` int(11)     NOT NULL,
  `prompt_version`  varchar(20) NOT NULL,
  `focus_sections`  text        DEFAULT NULL,
  `section_badges`  text        DEFAULT NULL,
  `section1_text`   text        NOT NULL,
  `section2_text`   text        NOT NULL,
  `section3_text`   text        NOT NULL,
  `created_at`      datetime(6) NOT NULL,
  PRIMARY KEY (`ai_guide_id`),
  UNIQUE KEY `uk_ai_guide` (`user_id`, `blog_post_id`, `profile_version`, `prompt_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
