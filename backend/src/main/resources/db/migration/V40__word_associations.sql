-- ============================================================
-- V36: 单词联想模块 — 同义词、衍生词、常用搭配
-- ============================================================
-- 该表用于缓存/存储单词联想数据，服务端也会从 ECDICT 动态计算
-- 如果表中无缓存则从 ECDICT/Words 表中实时解析并返回
-- ============================================================

CREATE TABLE IF NOT EXISTS `word_associations` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `word` varchar(100) NOT NULL COMMENT '查询单词（小写）',
    `type` varchar(20) NOT NULL COMMENT '联想类型：synonym(同义词)/derivative(衍生词)/collocation(搭配)',
    `associated_word` varchar(200) NOT NULL COMMENT '关联词汇',
    `definition` varchar(500) DEFAULT NULL COMMENT '关联词汇释义',
    `part_of_speech` varchar(50) DEFAULT NULL COMMENT '词性',
    `example_sentence` varchar(1000) DEFAULT NULL COMMENT '例句',
    `source` varchar(50) DEFAULT 'manual' COMMENT '数据来源：manual/ecdict/ai',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
    `created_time` datetime DEFAULT NULL COMMENT '创建时间',
    `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_word_type` (`word`, `type`),
    KEY `idx_word` (`word`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词联想表';

-- ============================================================
-- 预置常见高频单词的常用搭配 (collocations)
-- 这些搭配覆盖了英语学习中最常用的词组，后续可扩展
-- ============================================================

-- 常用动词搭配
INSERT INTO `word_associations` (`word`, `type`, `associated_word`, `definition`, `part_of_speech`, `example_sentence`, `source`) VALUES
('take', 'collocation', 'take care of', '照顾，处理', 'v.', 'Please take care of yourself.', 'manual'),
('take', 'collocation', 'take part in', '参加，参与', 'v.', 'She wants to take part in the competition.', 'manual'),
('take', 'collocation', 'take place', '发生，举行', 'v.', 'The meeting will take place tomorrow.', 'manual'),
('take', 'collocation', 'take advantage of', '利用', 'v.', 'You should take advantage of this opportunity.', 'manual'),
('take', 'collocation', 'take into account', '考虑，顾及', 'v.', 'We must take all factors into account.', 'manual'),

('make', 'collocation', 'make a decision', '做决定', 'v.', 'It is time to make a decision.', 'manual'),
('make', 'collocation', 'make progress', '取得进步', 'v.', 'He is making good progress in English.', 'manual'),
('make', 'collocation', 'make sure', '确保，确定', 'v.', 'Make sure you lock the door.', 'manual'),
('make', 'collocation', 'make use of', '利用，使用', 'v.', 'Make good use of your time.', 'manual'),
('make', 'collocation', 'make a difference', '有影响，起作用', 'v.', 'Your help really made a difference.', 'manual'),

('get', 'collocation', 'get along with', '与...相处', 'v.', 'She gets along well with her classmates.', 'manual'),
('get', 'collocation', 'get rid of', '摆脱，除去', 'v.', 'I need to get rid of this bad habit.', 'manual'),
('get', 'collocation', 'get used to', '习惯于', 'v.', 'It takes time to get used to the new environment.', 'manual'),
('get', 'collocation', 'get in touch', '取得联系', 'v.', 'I will get in touch with you later.', 'manual'),
('get', 'collocation', 'get involved in', '参与，卷入', 'v.', 'He got involved in the project.', 'manual'),

('put', 'collocation', 'put off', '推迟', 'v.', 'Never put off till tomorrow what you can do today.', 'manual'),
('put', 'collocation', 'put up with', '忍受，容忍', 'v.', 'I cannot put up with this noise anymore.', 'manual'),
('put', 'collocation', 'put forward', '提出', 'v.', 'He put forward a new proposal.', 'manual'),
('put', 'collocation', 'put on', '穿上；上演', 'v.', 'Put on your coat. It is cold outside.', 'manual'),

('give', 'collocation', 'give up', '放弃', 'v.', 'Never give up on your dreams.', 'manual'),
('give', 'collocation', 'give in', '屈服，让步', 'v.', 'She refused to give in to pressure.', 'manual'),
('give', 'collocation', 'give out', '分发；耗尽', 'v.', 'The teacher gave out the test papers.', 'manual'),
('give', 'collocation', 'give away', '赠送；泄露', 'v.', 'He gave away all his money to charity.', 'manual'),

('look', 'collocation', 'look forward to', '期待，盼望', 'v.', 'I look forward to hearing from you.', 'manual'),
('look', 'collocation', 'look after', '照顾', 'v.', 'Can you look after my dog while I am away?', 'manual'),
('look', 'collocation', 'look into', '调查', 'v.', 'The police are looking into the case.', 'manual'),
('look', 'collocation', 'look up', '查阅，查找', 'v.', 'Look up the word in the dictionary.', 'manual'),
('look', 'collocation', 'look down on', '看不起', 'v.', 'Never look down on others.', 'manual'),

('go', 'collocation', 'go on', '继续', 'v.', 'Please go on with your story.', 'manual'),
('go', 'collocation', 'go through', '经历；仔细检查', 'v.', 'She has gone through a lot of difficulties.', 'manual'),
('go', 'collocation', 'go over', '复习；仔细检查', 'v.', 'Let me go over the report one more time.', 'manual'),
('go', 'collocation', 'go ahead', '继续；去做吧', 'v.', 'You may go ahead with your plan.', 'manual'),

('come', 'collocation', 'come up with', '想出，提出', 'v.', 'She came up with a brilliant idea.', 'manual'),
('come', 'collocation', 'come across', '偶然遇到', 'v.', 'I came across an old friend yesterday.', 'manual'),
('come', 'collocation', 'come true', '实现，成真', 'v.', 'His dream finally came true.', 'manual'),
('come', 'collocation', 'come to an end', '结束', 'v.', 'The journey has come to an end.', 'manual'),

('turn', 'collocation', 'turn off', '关闭', 'v.', 'Please turn off the lights when you leave.', 'manual'),
('turn', 'collocation', 'turn on', '打开', 'v.', 'Can you turn on the air conditioner?', 'manual'),
('turn', 'collocation', 'turn down', '拒绝；调低', 'v.', 'She turned down the job offer.', 'manual'),
('turn', 'collocation', 'turn out', '结果是；证明是', 'v.', 'It turned out to be a great success.', 'manual'),
('turn', 'collocation', 'turn up', '出现；调高', 'v.', 'He didn''t turn up at the party.', 'manual'),

-- 常用名词搭配
('attention', 'collocation', 'pay attention to', '注意，关注', 'n.', 'Pay attention to what the teacher says.', 'manual'),
('effort', 'collocation', 'make an effort', '努力', 'n.', 'You need to make an effort to improve.', 'manual'),
('decision', 'collocation', 'make a decision', '做决定', 'n.', 'It is a difficult decision to make.', 'manual'),
('sense', 'collocation', 'make sense', '有意义，讲得通', 'n.', 'Does this sentence make sense?', 'manual'),
('contribution', 'collocation', 'make a contribution', '做贡献', 'n.', 'Everyone can make a contribution to society.', 'manual'),

-- 常用形容词搭配
('important', 'collocation', 'play an important role', '起重要作用', 'adj.', 'Education plays an important role in society.', 'manual'),
('aware', 'collocation', 'be aware of', '意识到', 'adj.', 'You should be aware of the risks.', 'manual'),
('responsible', 'collocation', 'be responsible for', '对...负责', 'adj.', 'Who is responsible for this project?', 'manual'),
('capable', 'collocation', 'be capable of', '有能力做', 'adj.', 'She is capable of handling this task.', 'manual'),
('interested', 'collocation', 'be interested in', '对...感兴趣', 'adj.', 'I am interested in learning languages.', 'manual'),

-- 更多常用动词搭配
('set', 'collocation', 'set up', '建立，设立', 'v.', 'They set up a new company last year.', 'manual'),
('set', 'collocation', 'set off', '出发；引爆', 'v.', 'We set off early in the morning.', 'manual'),
('set', 'collocation', 'set out', '出发；开始', 'v.', 'He set out to achieve his goals.', 'manual'),

('bring', 'collocation', 'bring about', '引起，导致', 'v.', 'Technology has brought about great changes.', 'manual'),
('bring', 'collocation', 'bring up', '抚养；提出', 'v.', 'She was brought up by her grandparents.', 'manual'),
('bring', 'collocation', 'bring out', '出版；使显现', 'v.', 'The new edition will be brought out next month.', 'manual'),

('break', 'collocation', 'break down', '分解；出故障', 'v.', 'The car broke down on the way.', 'manual'),
('break', 'collocation', 'break up', '分手；解散', 'v.', 'They decided to break up after five years.', 'manual'),
('break', 'collocation', 'break out', '爆发', 'v.', 'A fire broke out in the building.', 'manual'),
('break', 'collocation', 'break through', '突破', 'v.', 'Scientists have broken through in cancer research.', 'manual'),

('call', 'collocation', 'call for', '需要；呼吁', 'v.', 'This situation calls for immediate action.', 'manual'),
('call', 'collocation', 'call off', '取消', 'v.', 'The match was called off due to rain.', 'manual'),
('call', 'collocation', 'call on', '拜访；号召', 'v.', 'I will call on you tomorrow.', 'manual'),

('carry', 'collocation', 'carry out', '执行，实施', 'v.', 'We need to carry out the plan immediately.', 'manual'),
('carry', 'collocation', 'carry on', '继续', 'v.', 'Carry on with your work.', 'manual'),

('hold', 'collocation', 'hold on', '坚持；等一下', 'v.', 'Hold on a moment, please.', 'manual'),
('hold', 'collocation', 'hold back', '抑制；阻止', 'v.', 'She could not hold back her tears.', 'manual'),
('hold', 'collocation', 'hold up', '举起；耽搁', 'v.', 'The traffic held us up for an hour.', 'manual'),

('keep', 'collocation', 'keep up with', '跟上，不落后', 'v.', 'It is hard to keep up with the latest technology.', 'manual'),
('keep', 'collocation', 'keep in mind', '记住', 'v.', 'Keep in mind that practice makes perfect.', 'manual'),
('keep', 'collocation', 'keep on', '继续', 'v.', 'Keep on trying and you will succeed.', 'manual'),

('run', 'collocation', 'run out of', '用完，耗尽', 'v.', 'We have run out of time.', 'manual'),
('run', 'collocation', 'run into', '偶遇；遭遇', 'v.', 'I ran into an old colleague at the airport.', 'manual'),

('work', 'collocation', 'work out', '解决；锻炼；算出', 'v.', 'Let us work out a solution together.', 'manual'),
('work', 'collocation', 'work on', '从事于，致力于', 'v.', 'I am working on a new project.', 'manual'),

('figure', 'collocation', 'figure out', '弄清楚，理解', 'v.', 'I cannot figure out what he means.', 'manual'),

('deal', 'collocation', 'deal with', '处理，应对', 'v.', 'How do you deal with stress?', 'manual'),

('depend', 'collocation', 'depend on', '依赖，取决于', 'v.', 'Success depends on hard work.', 'manual'),

('lead', 'collocation', 'lead to', '导致，通向', 'v.', 'Hard work leads to success.', 'manual'),

('result', 'collocation', 'result in', '导致，造成', 'v.', 'The accident resulted in three deaths.', 'manual'),

('consist', 'collocation', 'consist of', '由...组成', 'v.', 'The team consists of five members.', 'manual'),

('belong', 'collocation', 'belong to', '属于', 'v.', 'This book belongs to me.', 'manual'),

('focus', 'collocation', 'focus on', '集中于，专注于', 'v.', 'We should focus on the main issue.', 'manual'),

('refer', 'collocation', 'refer to', '提及；参考', 'v.', 'Please refer to the instructions for details.', 'manual'),

('apply', 'collocation', 'apply for', '申请', 'v.', 'She applied for a scholarship.', 'manual'),

('search', 'collocation', 'search for', '搜索，寻找', 'v.', 'They are searching for the missing child.', 'manual'),

('provide', 'collocation', 'provide with', '提供', 'v.', 'The hotel provides guests with free breakfast.', 'manual'),

('compare', 'collocation', 'compare with', '与...比较', 'v.', 'Compared with last year, sales have increased.', 'manual'),

('communicate', 'collocation', 'communicate with', '与...交流', 'v.', 'It is important to communicate with your team.', 'manual'),

('connect', 'collocation', 'connect with', '与...连接；联系', 'v.', 'I need to connect with more people in my field.', 'manual'),

('struggle', 'collocation', 'struggle with', '与...斗争', 'v.', 'Many students struggle with math.', 'manual'),

('experiment', 'collocation', 'experiment with', '用...做实验', 'v.', 'The artist experiments with different styles.', 'manual'),

-- 常用介词搭配
('according', 'collocation', 'according to', '根据，按照', 'prep.', 'According to the report, sales have increased.', 'manual'),
('regard', 'collocation', 'in regard to', '关于', 'v.', 'In regard to your question, here is my answer.', 'manual'),
('spite', 'collocation', 'in spite of', '尽管，不顾', 'n.', 'In spite of the rain, we went out.', 'manual'),
('addition', 'collocation', 'in addition to', '除...之外', 'n.', 'In addition to English, she speaks French.', 'manual'),
('account', 'collocation', 'on account of', '因为，由于', 'n.', 'The game was cancelled on account of rain.', 'manual'),
('behalf', 'collocation', 'on behalf of', '代表', 'n.', 'I am speaking on behalf of my team.', 'manual'),
('favor', 'collocation', 'in favor of', '支持，赞成', 'n.', 'Most people are in favor of the new policy.', 'manual'),
('charge', 'collocation', 'in charge of', '负责，管理', 'n.', 'Who is in charge of this department?', 'manual'),
('relation', 'collocation', 'in relation to', '与...相关', 'n.', 'What is your role in relation to the project?', 'manual');
