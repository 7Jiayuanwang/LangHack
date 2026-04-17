-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:14
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: Limits and the definition of derivatives
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'Limits and the definition of derivatives',
    'The last several lessons have been about the idea of a derivative, and before moving on to integrals, I want to take some time to talk about limits.

To be honest, the idea of a limit is not really anything new. If you know what the word "approach" means you pretty much already know what a limit is, you could say the rest is a matter of assigning fancy notation to the intuitive idea of one value that just gets closer to another. But there are a number of reasons to dive deeper into this topic.

For one thing it’s worth showing is how the way I’ve been describing derivatives so far lines up with the the formal definition of a derivative as it’s typically presented in most courses and textbooks. I want to give you a little confidence that thinking of terms like dx and df as concrete non-zero nudges is not just some trick for building intuition; it’s actually backed up by the formal definition of a derivative in all of its rigor.

Let’s take a look at the formal definition of the derivative. As a reminder, when you have some function f(x), to think about the derivative at a particular input, maybe x=2, you start by imagining nudging that input by some tiny dx, and looking at the resulting change to the output, df.

The ratio df/dx, which can nicely be thought of as the rise-over-run slope between the starting point on the graph and the nudged point, is almost what the derivative is. The actual derivative is whatever this ratio approaches as dx approaches 0.

Just to spell out what is meant here, that nudge to the output "df" is is the difference between f at the starting-input plus dx and f at the starting-input; the change to the output caused by the nudge dx.

To express that you want to find what this ratio approaches as dx approaches 0, you write "lim", for limit, with "dx arrow 0" below it.

Now, you’l almost never see terms with a lowercase d, like dx, inside a limit like this. Instead the standard is to use a different variable, like Δx\Delta xΔx (delta x), or commonly "h" for some reason.

The way I like to think of it is that terms with this lowercase d in the typical derivative expression have built into them the idea of a limit, the idea that dx is supposed to eventually approach 0.

So in a sense this lefthand side "df/dx", the ratio we’ve been thinking about in the past lessons, is just shorthand for what the righthand side spells out in more detail, writing out exactly what we mean by df, and writing out this limit process explicitly. And that righthand side is the formal definition of a derivative, as you’d commonly see it in any calculus textbook.

The formal derivative definition. Notice that nothing about this definition references the idea of an "infinitely small" change.

Now, if you’l pardon me for a small rant here, I want to emphasize that nothing about this righthand side references the paradoxical idea of an "infinitely small" change. The point of limits is to avoid that.

This value h is the exact same thing as the "dx" I’ve been referencing throughout the series. It’s a nudge to the input of f with some non-zero, finitely small size, like 0.001, that we’re analyzing for arbitrarily small choices of h. In fact, the reason people introduce a new variable name into this formal definition, rather than just using dx, is to be clear that these changes to the input are ordinary numbers that have nothing to do with infinitesimals.1

Rather than interpret dx as an "infinitely small change", as others like to do, I think you can and should interpret dx as a concrete, finitely small nudge, just so long as you remember to ask what happens as it approaches 0. This method helps build a stronger intuition for where the rules of calculus come from and, more than that, it’s not just some trick for building intuitions. Everything I’ve been saying about derivatives with this concrete-finitely-small-nudge philosophy is a translation of the formal definition of derivatives.

Long story short, the big fuss about limits is that they let us avoid talking about infinitely small changes by instead asking what happens as the size of some change to our variable approaches 0.

So why do we teach the limit notation and how does it help us formalize what it means for one value to approach another?

Maybe you are convinced that, on an intuitive level, the language and tools we have invented so far give us what we need to solve these types of problems. When thinking about small nudges to a function''s input, as long as we imagine that nudge approaching zero, things usually work out. In the same way, we can zoom in on a point on a curve and convince ourselves that if we zoom in far enough the curve really starts to look straight.

Zoom in on the exponential function exe^xex at x=0 and the rate of change (slope) starts to look a whole lot like 1.

But, you may still have doubts and questions, as certainly some of the creators of calculus did. For example, just how far do we have to zoom in on a point before a function starts to look like its tangent line at that point?

If you are applying calculus, your primary concern is probably the accuracy of the approximation and you might play a cat and mouse game to choose a small enough dx so that the approximation is is accurate enough to calculate a useful result. A mathematician, on the other hand, is playing a different game; they are interested in whether they can rig the cat and mouse game of choosing smaller and smaller values and getting a more accurate result so that they will always win.

Even more than this, mathematicians care about ensuring all their statements have a precise and airtight meaning, which does not rest on visual intuition. As sensible as it might seem to say that this ratio approaches a certain value, and to draw a line through the graph whose slope approaches that of a tangent line, those seeking rigor insist on something better. In the next chapter, we’l take a look the epsilon-delta definition of limits, which finally puts this idea of "approaching" onto a firm foundation.

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
