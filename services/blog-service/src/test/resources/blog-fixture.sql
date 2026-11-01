DELETE FROM blog_post_category;
DELETE FROM blog_post_tag;
DELETE FROM blog_post_section;
DELETE FROM blog_post;
DELETE FROM company;
DELETE FROM tech_tag;
INSERT INTO company(company_id,name,feed_url,feed_type) VALUES (1,'Alpha','https://a.test/feed','RSS'),(2,'Beta','https://b.test/feed','RSS'),(3,'Empty','https://c.test/feed','RSS');
INSERT INTO tech_tag(tech_tag_id,name,field,post_count,selectable) VALUES (10,'Spring','Backend',2,true),(20,'React','Frontend',2,true);
INSERT INTO blog_post(blog_post_id,company_id,title,published_at,content_html,content_text,char_count,has_code,collected_at,is_tech,field,level,url,summary) VALUES
(101,1,'Spring intro','2026-09-01','<h2>Start</h2><img src="/logo.png"><p>hello</p>','spring text',20,false,'2026-09-01',true,'Backend','L1','https://a.test/101','Spring overview'),
(102,1,'React intro','2026-09-02','<p>react</p>','react text',10,false,'2026-09-02',true,'Frontend','L1','https://a.test/102','React overview'),
(103,1,'General Spring','2026-09-03','<p>general</p>','general text',10,false,'2026-09-03',NULL,'General','L1','https://a.test/103','General overview'),
(104,1,'Not tech','2026-09-04','<p>ignore</p>','ignore',10,false,'2026-09-04',false,'Backend','L1','https://a.test/104','Excluded'),
(201,2,'React second','2026-09-02','<p>react2</p>','react2',10,false,'2026-09-02',true,'Frontend','L1','https://b.test/201','Another React');
INSERT INTO blog_post_tag(blog_post_id,tech_tag_id,tag_rank) VALUES (101,10,1),(102,20,1),(103,10,1),(104,10,1),(201,20,1);
INSERT INTO blog_post_section(section_id,blog_post_id,seq,heading,content_text,char_count) VALUES (1,101,1,'Start','hello',20);
