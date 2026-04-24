-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:15
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: (ε, δ) "epsilon delta" definitions of limits
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    '(ε, δ) "epsilon delta" definitions of limits',
    '- Bertrand Russell

In the previous lesson, I introduced limits and how they are used to formalize the definition of the derivative. In this lesson our goal is to understand exactly what it means for one value to approach another. For example, consider the function:

This happens to be the expression that pops out if you unravel the formal definition of the derivative of x3x^3x3 at x=2.

However, let’s just think of it as any old function with an input h. Its graph is this nice continuous looking parabola. Which makes sense because the function has a cubic term divided by a linear term.

But actually, if you think about what’s going at h=0, plugging that in you’d get 0/0, which is not defined. So really, this graph has a hole at that point.

You have to exaggerate to draw that hole, often with a little empty circle like this, but keep in mind the function is perfectly well-defined for inputs as close to 0 as you want.

And wouldn’t you agree that as h approaches 0, the corresponding output, the height of this graph, approaches 12? And it doesn’t matter which side you come at it from. That the limit of this ratio as h goes to 0 equals 12.

Imagine you’re a mathematician inventing calculus, and someone skeptically asks "well what exactly do you mean by approach?" That would be an annoying question. I mean, come on, we all know what it means for one value to get closer to another. But let''s start thinking about ways you might be able to answer that person completely unambigously.

For a given range of inputs within some distance of 0, excluding the forbidden point 0, look at the corresponding outputs, all possible heights of the graph above that range.

This figure shows the possible output ranges for 0 plus or minus 0.1, 0.01 and 0.001

As that range of input values closes in more and more tightly around 0, the range of output values closes in more and more closely around 12. Importantly, the size of that range of outputs can be made as small as you want.

Can you verify this result using algebra?

Most definitely and it''s worthwhile to check that two different ways to think about the same problem come to the same conclusion.

Start by setting up the limit expression.

Expand the first term in the numerator.

Notice the first and last terms in the numerator cancel each other out.

Since all of the terms in the numerator share the factor h, the fraction simplifies.

Now as h approaches 0 the terms containing h will become negible, giving us the same resullt.

As a counter example, consider a function that looks like the one shown below, which is also not defined at 0, but kind of jumps at that point.

As you approach h=0h = 0h=0 from the right, the function approaches 2, but as you come at 0 from the left, it approaches 1. Since there’s not a clear, unambiguous value that this function approaches as h approaches 0, the limit is simply not defined at that point.

One way to think of this is that when you look at any range of inputs around 0, and the corresponding range of outputs, as you shrink that input range the corresponding outputs don’t narrow in on any specific value. Instead those outputs straddle a range that never even shrinks smaller than 1, no matter how small your input range.

This perspective of shrinking an input range around the limiting point, and seeing whether or not you’re restricted in how much that shrinks the output range, leads to something called the "epsilon delta" definition of limits.

You’ve already seen the main idea: when a limit exists, you can make this output range as small as you want; but when the limit doesn’t exist, that output range can’t get smaller than some value, no matter how much you shrink the input range around the limiting input.

The limit lim⁡h→af(h)\displaystyle \lim _{h \rightarrow a} f(h)h→alim​f(h) exists when the output range defined by ϵ\epsilonϵ (epsilon) can be made as small as you want and you can still find some δ\deltaδ (delta) which corresponds to the output range.

Let''s phrase that same idea, but a little more precisely. In the context of this example where the limiting value was 12, think of any distance away from 12, where it’s common to use the greek letter ϵ\epsilonϵ "epsilon" to denote that distance. And the intent here is going to be that this distance ϵ\epsilonϵ can be as small as you want.

What it means for the limit to exist is that you will always be able to find a range of inputs around our limiting input, some distance delta away from 0, so that any input within a distance delta of 0 corresponds to an output within a distance epsilon of 12.

They key point is that this is true for any epsilon, no matter how small, you will always be able to find the corresponding delta.

What is the δ\deltaδ (delta) value corresponding to the ouput range [1.994,12.006][1.994, 12.006][1.994,12.006] where ϵ\epsilonϵ (epsilon) is equal to 0.006?

In contrast, when a limit does not exist, as in the example before, you can find a sufficiently small epsilon, like 0.4, so that no matter how small you make your range around 0, no matter how tiny delta is, the corresponding range of outputs is just always too big.

There is no limiting output where everything is within a distance epsilon from that output.

In this lesson we established the epsilon delta definition of limits to formalize what we mean when we say one value approaches another. This gives us a strong foundation to build upon and makes our argument air-tight when we talk about such tools as the derivative. In the next lesson I talk about a neat trick for computing limits.

Using the epsilon delta definition, is the limit expression lim⁡x→01x+x\displaystyle \lim _{x \rightarrow 0} \frac{1}{x} + x→0lim​x1​+x defined?

Using the epsilon delta definition, is the limit expression lim⁡x→1x−1x3−1\displaystyle \lim _{x \rightarrow 1} \frac{x-1}{x^3 - 1}x→1lim​x3−1x−1​ defined?

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
