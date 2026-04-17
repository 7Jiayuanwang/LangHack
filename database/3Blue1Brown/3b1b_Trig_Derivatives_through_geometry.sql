-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:08
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: Trig Derivatives through geometry
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'Trig Derivatives through geometry',
    'For example, how would you interpret the value sin⁡(0.8)\sin(0.8)sin(0.8) if the value θ=0.8\theta = 0.8θ=0.8 is understood to be in radians? You might imagine walking around a circle with a radius of 1, starting from the rightmost point, until you’ve traversed the distance 0.8 in arc length. This is the same thing as saying you''ve traversed an angle of 0.8 radians. Then sin⁡(θ)\sin(\theta)sin(θ) is your height above the x-axis at this point.

As theta increases, and you walk around the circle, your height bobs up and down and up and down. So the graph of sin⁡(θ)\sin(\theta)sin(θ) vs. θ\thetaθ, which plots this height as a function of arc length, is a wave pattern. This is the quintessential wave pattern.

Just from looking at this graph, we can get a feel for the shape of the derivative function. The slope at 0 is something positive, then as sin⁡(θ)\sin(\theta)sin(θ) approaches its peak, the slope goes down to 0. Then the slope is negative for a little while before coming back up to 0 as the sin⁡(θ)\sin(\theta)sin(θ) graph levels out. If you’re familiar with the graphs of trig functions, you might guess that this derivative graph should be exactly cos⁡(θ)\cos(\theta)cos(θ), whose graph is just a shifted-back copy of the sine graph.

But all this tells us is that the peaks and valleys of the derivative graph seem to line up with the graph of cosine. How could we know that this derivative actually is the cosine of theta, and not just some new function that looks similar to it? As with the previous examples of this video, a more exact understanding of the derivative requires looking at what the function itself represents, rather than the graph of the function.

Think back to the walk around the unit circle, having traversed an arc length of θ\thetaθ, where sin⁡(θ)\sin(\theta)sin(θ) is the height of this point. Consider a slight nudge of d-theta along the circumference of the circle; a tiny step in your walk around the unit circle. How much does this change sin⁡(θ)\sin(\theta)sin(θ)? How much does that step change your height above the x-axis? This is best observed by zooming in on the point where you are on the circle.

Zoomed in close enough the circle basically looks like a straight line in this neighborhood. Consider the right triangle pictured below, where the hypotenuse represents a straight-line approximation of the nudge dθd \thetadθ along the circumference, and this left side represents the change in height; the resulting tiny nudge to sin⁡(θ)\sin(\theta)sin(θ).

This tiny triangle is actually similar to this larger triangle with the defining angle theta, and whose hypotenuse is the radius of the circle with length 1. Specifically, the angle between its height d(sin⁡(θ))d(\sin(\theta))d(sin(θ)) and its hypotenuse dθd\thetadθ is precisely equal to θ\thetaθ.

Think about what the derivative of sine is supposed to mean. It’s the ratio between that d(sin⁡(θ))d\left(\sin(\theta)\right)d(sin(θ)), the tiny change to the output of sine, divided by dθd \thetadθ, the tiny change to the input of the function. From the picture, that’s the ratio between the length of the side adjacent to this little right triangle divided by the hypotenuse. Well, let’s see, adjacent divided by hypotenuse; that’s exactly what cos⁡(θ)\cos(\theta)cos(θ) means!

Notice, by considering the slope of the graph, we can get a quick intuitive feel for the rough shape that the derivative of sin⁡(θ)\sin(\theta)sin(θ) should have, which is enough to make an educated guess. But to more to understand why this derivative is precisely cos⁡(θ)\cos(\theta)cos(θ), we had to begin our line of reasoning with the defining features of sin⁡(θ)\sin(\theta)sin(θ).

For those of you who enjoy pausing and pondering, take a moment to find a similar line of reasoning that explains what the derivative of cos⁡(θ)\cos(\theta)cos(θ) should be.

In the next lesson we''l figure out the derivatives of functions that combine simple functions like these, either as sums, products, or functions compositions. Similar to this lesson, we’l try to understand each rule geometrically, in a way that makes it intuitively reasonable and memorable.

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
