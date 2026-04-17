-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:23
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: Taylor series (geometric view)
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'Taylor series (geometric view)',
    'Like we did in those videos, consider a function that gives the area under some graph between a fixed left point and a variable right point. What we''re going to do is think about how to approximate this area function, not the function for the graph like we were doing before. Focusing on that area is what will make the second order term pop out.

Remember, the fundamental theorem of calculus is that this graph itself represents the derivative of the area function, and as a reminder it''s because a slight nudge dx to the right bound on the area gives a new bit of area approximately equal to the height of the graph times dx, in a way that''s increasingly accurate for smaller choice of dx.

So df over dx, the change in area divided by that nudge dx, approaches the height of the graph as dx approaches 0.

But if you wanted to be more accurate about the change to the area given some change to x that isn''t mean to approach 0, you would take into account this portion right here, which is approximately a triangle.

Let''s call the starting input a, and the nudged input above it x, so that this change is (x-a).

The base of that little triangle is that change (x-a), and its height is the slope of the graph times (x-a). Since this graph is the derivative of the area function, that slope is the second derivative of the area function, evaluated at the input a.

So the area of that triangle, ½ base times height, is one half times the second derivative of the area function, evaluated at a, multiplied by (x-a)2.

And this is exactly what you see with Taylor polynomials. If you knew the various derivative information about the area function at the point a, you would approximate this area at x to be the area up to a, f(a), plus the area of this rectangle, which is the first derivative times (x-a), plus the area of this triangle, which is ½ (the second derivative) * (x - a)2.

I like this, because even though it looks a bit messy all written out, each term has a clear meaning you can point to on the diagram.

Special thanks to those below for supporting the original video behind this post, and to current patrons for funding ongoing projects. If you find these lessons valuable, consider joining.',
    'HARD',
    '数学,微积分,calculus,3Blue1Brown,英语学习',
    NULL,
    1,
    'APPROVED',
    0,
    0,
    1,
    0,
    NOW(),
    NOW()
);

-- ============================================================
-- 完成
-- ============================================================
SELECT '文章导入完成!' AS message;
SELECT COUNT(*) AS total_articles FROM texts WHERE unit_id = 999 AND deleted = 0;
