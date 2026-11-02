-- blog-service (ddl-auto: validate) 테이블. 기준: mini8-content-dump.sql DDL, blog-service 엔티티
-- 데이터는 이미지 빌드 시 덤프에서 추출한 11-blog-seed.sql 이 넣는다.
USE blog_service;

CREATE TABLE IF NOT EXISTS `company` (
  `company_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `checked_at` datetime(6) DEFAULT NULL,
  `feed_type` varchar(20) NOT NULL,
  `feed_url` varchar(255) NOT NULL,
  `logo_url` varchar(500) DEFAULT NULL,
  `main_business` varchar(300) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `source_url` varchar(255) DEFAULT NULL,
  `summary` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `tech_tag` (
  `tech_tag_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `aliases` varchar(255) DEFAULT NULL,
  `field` varchar(30) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `post_count` int(11) NOT NULL,
  `selectable` bit(1) NOT NULL,
  PRIMARY KEY (`tech_tag_id`),
  UNIQUE KEY `UKijaja9j8t5hlb0d0ysy433hfn` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `blog_post` (
  `blog_post_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `char_count` int(11) NOT NULL,
  `collected_at` datetime(6) NOT NULL,
  `content_html` mediumtext NOT NULL,
  `content_text` mediumtext NOT NULL,
  `external_id` varchar(255) DEFAULT NULL,
  `field` varchar(10) DEFAULT NULL,
  `has_code` bit(1) NOT NULL,
  `is_tech` bit(1) DEFAULT NULL,
  `level` varchar(4) DEFAULT NULL,
  `published_at` datetime(6) NOT NULL,
  `summary` varchar(300) DEFAULT NULL,
  `title` varchar(300) NOT NULL,
  `url` varchar(500) NOT NULL,
  `company_id` bigint(20) NOT NULL,
  PRIMARY KEY (`blog_post_id`),
  UNIQUE KEY `UKb7mf3v2h8rmnl8tfryr1h9167` (`url`),
  KEY `FKfvl0dmscnom57ofqf40f40hrv` (`company_id`),
  CONSTRAINT `FKfvl0dmscnom57ofqf40f40hrv` FOREIGN KEY (`company_id`) REFERENCES `company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `blog_post_section` (
  `section_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `char_count` int(11) NOT NULL,
  `content_text` mediumtext NOT NULL,
  `heading` varchar(300) NOT NULL,
  `level` varchar(4) DEFAULT NULL,
  `seq` int(11) NOT NULL,
  `blog_post_id` bigint(20) NOT NULL,
  PRIMARY KEY (`section_id`),
  UNIQUE KEY `uk_blog_post_section` (`blog_post_id`,`seq`),
  CONSTRAINT `FKj3qg39k3qclxdgt5hq86tnpov` FOREIGN KEY (`blog_post_id`) REFERENCES `blog_post` (`blog_post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `blog_post_tag` (
  `tag_rank` int(11) DEFAULT NULL,
  `blog_post_id` bigint(20) NOT NULL,
  `tech_tag_id` bigint(20) NOT NULL,
  PRIMARY KEY (`blog_post_id`,`tech_tag_id`),
  KEY `FKs6oc10jhfwp0qag8m1xewwf99` (`tech_tag_id`),
  CONSTRAINT `FK2oneu8y2qquwki582p82fy2cy` FOREIGN KEY (`blog_post_id`) REFERENCES `blog_post` (`blog_post_id`),
  CONSTRAINT `FKs6oc10jhfwp0qag8m1xewwf99` FOREIGN KEY (`tech_tag_id`) REFERENCES `tech_tag` (`tech_tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `blog_post_category` (
  `name` varchar(255) NOT NULL,
  `blog_post_id` bigint(20) NOT NULL,
  PRIMARY KEY (`blog_post_id`,`name`),
  CONSTRAINT `FKspj3x703gudi58126om3weqqf` FOREIGN KEY (`blog_post_id`) REFERENCES `blog_post` (`blog_post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

