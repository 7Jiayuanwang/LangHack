-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:21
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: Higher order derivatives
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'Higher order derivatives',
    'Somehow I''ve managed not to bring up higher order derivatives at all so far this series, so for the sake of completeness, I thought I''d give this little footnote to briefly go over them. I''l focus mainly on the second derivative, showing what it looks like in the context of graphs and motion, and leave you to think about the analogies for higher orders.

Given some function f(x), the derivative can be interpreted as the slope of its graph above some input, right? A steep slope means a high value for the derivative, a downward slope means a negative derivative.

Review question: what is the slope at x=2 and x=4?

The second derivative, whose notation I''l explain in a moment, is the derivative of the derivative, meaning it tells you how that slope is changing. The way to see this at a glance is to think of how the graph of f(x) curves. At points where it curves upward, the slope is increasing, so the second derivative is positive. At points where it curves downward, the slope is decreasing, so the second derivative is negative.

For example, let''s plot the derivative and second derivative alongside the original function. The plot tells us a lot about how the slope is changing: At x=1 the slope is decreasing at a rate of −1-1−1, at x=2 the slope isn''t changing at all, and at x=3 the slope is increasing at a rate of 1.

As far as notation goes, you could try writing it like this:

. indicating some small change to the derivative function divided by some small change to x, where as always the use of that letter d suggests that you really want to consider what this ratio approach as dx, both dx''s in this case, approach 0. That''s pretty awkward and clunky, so the standard is to abbreviate it as d2fdx2\frac{d^{2}f}{dx^2}dx2d2f​.

It''s not terribly important for getting an intuition of the second derivative, but perhaps it''s worth showing how you can read this notation. Think of starting at some input to your function, and taking two small steps to the right, each with a size dx. I''m choosing rather big steps here so that we''l better see what''s going on, but in principle think of them as rather tiny.

The first step causes some change to the function, which I''l call df1df_1df1​, and the second step causes some similar, but possibly slightly different change, which I''l call df2df_2df2​.

The difference between these; the change in how the function changes, is what we''l call d(df).

You should think of this as really small, typically proportional to the size of (dx)2(dx)^2(dx)2. So if your choice for dx was 0.01, you''d expect this d(df) to be proportional to 0.001.

And the second derivative is the size of this change to the change, divide by the size of (dx)2(dx)^2(dx)2. Or, more precisely, it''s whatever that ratio approaches as dx approaches 0. Even though it''s not like the letter d is a variable being multiplied by f, for the sake of more compact notation you write this as d2fdx2\frac{d^2f}{dx^2}dx2d2f​, and you don''t bother with any parentheses on the bottom.

For higher order derivatives, like the third derivative, fourth derivative and so are are written using the same compact notation.

Maybe the most visceral understanding of the second derivative is that it represents acceleration.

Given some movement along a line, suppose you have some function that records distance traveled vs. time, and maybe its graph looks something like this, steadily increasing over time.

Then its derivative tells you velocity at each point in time, right? For the example, the graph might look like this bump, increasing to some maximum, then decreasing back to 0.

Then its second derivative tells you the rate of change for velocity, the acceleration at each point in time.

In the example, the second derivative is positive for the first half of the journey, which indicates indicates speeding up. That''s sensation of being pushed back into your car seat with a constant force. Or rather, having the car seat push you with a constant force.

The third derivative, and this is not a joke, is called jerk. So if the jerk is not zero, it means the strength of the acceleration itself is changing.

A Pi Creature is aboard a spaceship and is set to journey into space. The height s(t) of the spaceship from the ground, in meters, as a function of time t, in seconds, is given by:

Based on the displacement function s(t), determine the velocity function v(t) of the spaceship.

v(t)=4t3−12t2+12tv(t) = 4t^3 - 12t^2 + 12tv(t)=4t3−12t2+12t

v(t)=4t3−12t2+6tv(t) = 4t^3 - 12t^2 + 6tv(t)=4t3−12t2+6t

v(t)=4t2−8t+6v(t) = 4t^2 - 8t + 6v(t)=4t2−8t+6

v(t)=t3−3t2+2tv(t) = t^3 - 3t^2 + 2tv(t)=t3−3t2+2t

Given the velocity function from the previous question, what is the acceleration function a(t) of the spaceship?

a(t)=12t−24a(t) = 12t - 24a(t)=12t−24

a(t)=12t2−24ta(t) = 12t^2 - 24ta(t)=12t2−24t

a(t)=6t−12a(t) = 6t - 12a(t)=6t−12

a(t)=12t2−24t+12a(t) = 12t^2 - 24t + 12a(t)=12t2−24t+12

Determine the jerk function j(t) of the spaceship, which is the rate of change of acceleration.

j(t)=24t−24j(t) = 24t - 24j(t)=24t−24

j(t)=24tj(t) = 24tj(t)=24t

j(t)=12j(t) = 12j(t)=12

j(t)=24t+24j(t) = 24t + 24j(t)=24t+24

If you plot the acceleration of the function a(t), how would you interpret the motion of the spaceship at t=1 second?

If you plot the acceleration function a(t), you''l notice that at t=1 second, the acceleration dips to be 0 before increasing again.

One explanation is that this as a two-stage rocket where the first stage completes its burn and then there''s a momentary pause before the second stage ignites. The momentary standstill in the change of acceleration, or the point where the jerk becomes zero, is when the spaceship is making that transition.

One of the most useful things about higher order derivatives is how they help in approximating functions, which is the topic of the next chapter on Taylor series, so I''l see you there .',
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
