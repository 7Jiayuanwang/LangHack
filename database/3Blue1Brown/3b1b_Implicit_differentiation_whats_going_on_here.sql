-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:13
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: Implicit differentiation, what's going on here?
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'Implicit differentiation, what''s going on here?',
    'Let me share with you something I found particularly weird when I was a student first learning calculus.

Let''s say you have a circle with radius 5 centered at the origin of the xy-coordinate plane. This shape is defined using the equation x2+y2=52x^2 + y^2 = 5^2x2+y2=52. That is, all points on this circle are a distance 5 from the origin, as encapsulated by the pythagorean theorem where the sum of the squares of the legs of this triangle equals the square of the hypotenuse, 525^252.

And suppose you want to find the slope of a tangent line to this circle, maybe at the point (x,y)=(3,4)(x, y) = (3, 4)(x,y)=(3,4).

Now, if you''re savvy with geometry, you might already know that this tangent line is perpendicular to the radius line touching that point. But let''s say you don''t already know that, or that you want a technique that generalizes to curves other than circles.

As with other problems about the slopes of tangent lines to curves, they key thought here is to zoom in close enough that the curve basically looks just like its own tangent line, then ask about a tiny step along that curve.

The y-component of that little step along the curve is what you might call dy, and the x-component is a little dx, so the slope that we want is the rise over run dy/dx.

But unlike other tangent-slope problems in calculus, this curve is not the graph of a function. We cannot take a simple derivative, like we have in the past, by asking about the size of a tiny nudge to the output of a function caused by some tiny nudge to the input. In the equation x is not an input and y is not an output, they''re both just interdependent values related by some equation.

This is called an "implicit curve"; it''s just the set of all points (x,y)(x, y)(x,y) that satisfy some property written in terms of the two variables x and y.

The procedure for how you actually find dy/dx for curves like this is the thing I found very weird as a calculus student. You take the derivative of both sides of this equation like this:

You can see why this feels strange, right? What does it mean to take a derivative of an expression with multiple variables? And why are we tacking on the little dy and dx in this way? Unlike with functions, there isn''t clear flow from input to output. But if you just blindly move forward with what you get here, you can rearrange this equation to find an expression for dy/dx, which in this case comes out to −x/y-x/y−x/y.

So at a point with coordinates (x,y)=(3,4)(x, y) = (3, 4)(x,y)=(3,4), that slope would be −3/4-3/4−3/4, evidently. This strange process is called "implicit differentiation". Don''t worry, I have an explanation for how you can interpret taking a derivative of an expression with two variables like this. But first, I want to set aside this particular problem, and show how this is related to a different type of calculus problem, something called a related rates problem.

Imagine a 5 meter long ladder held up against a wall, where the top of the ladder starts off 4 meters above the ground, which—by the pythagorean theorem—means the bottom is 3 meters away from the wall.

And say it''s slipping down the wall in such a way that the top of the ladder is dropping at 1 meter per second. In that initial moment, what is the rate at which the bottom of the ladder is moving away from the wall?

It''s interesting, right? That distance from the bottom of the ladder to the wall is 100%100\%100% determined by the distance between the top of the ladder and the floor, so we should have enough information to figure out how the rates of change for each value depend on each other, but it might not be entirely clear how exactly you relate those two.

First thing''s first, it''s always nice to give names to the quantities we care about. So label the distance from the top of the ladder to the ground y(t), written as a function of time because it''s changing. Likewise, label the distance between the bottom of the ladder and the wall x(t). They key equation that relates these terms is the pythagorean theorem: x(t)2+y(t)2=52x(t)^2 + y(t)^2 = 5^2x(t)2+y(t)2=52. What makes this equation powerful is that it''s true at all points in time.

One way to solve this would be to isolate x(t), figure out what what y(t) must be based this 1 meter/second drop rate, then take a derivative of the resulting function; dx/dt, the rate at which x is changing with respect to time.

And that''s fine; if you''re willing to apply the chain rule a few times, this method will definitely work for you. But I want to show a different way to think about the same problem.

This left-hand side of the equation is a function of time, right? Which just so happens to equal a constant, meaning this value evidently doesn''t change while time passes.

However, this expression is dependent on time which we can manipulate like any other function with t as an input. In particular, we can take a derivative of the left hand side, which is a way of saying "If I let a little bit of time, dt, pass, which causes y to slightly decrease, and x to slightly increase, how much does this expression change?"

On the one hand, we know that derivative should be 0, since this expression is a constant, and constants don''t care about your tiny nudge to time; they remain unchanged. But on the other hand, what do you get when you compute the derivative of this as a function of time?

The derivative of x(t)2x(t)^2x(t)2 is 2⋅x(t)2 \cdot x(t)2⋅x(t) times the derivative of x. That''s the chain rule in action. 2x⋅dx2x \cdot dx2x⋅dx represents the size of a change to x2x^2x2 caused by a change to x, and then we''re dividing out by dt. Likewise, the rate at which y(t)2y(t)^2y(t)2 is changing is 2⋅y(t)2 \cdot y(t)2⋅y(t) times the derivative of y.

Now, evidently, this whole expression must be zero, and that''s an equivalent of saying x2+y2x^2+y^2x2+y2 must not change while the ladder moves. And at the very start, t=0, the height y(t) is 4 meters, the distance x(t) is 3 meters, and since the top of the ladder is dropping at a rate of 1 meter per second, that derivative dy/dt is −1-1−1 meters/second.

Now this gives us enough information to isolate the derivative dx/dt, which, when you work it out, it comes out to be 4/3 meters per second.

Now compare this to the problem of finding the slope of the tangent line to the circle. In both cases, we had the equation x2+y2=52x^2 + y^2 = 5^2x2+y2=52, and in both cases we ended up taking the derivative of each side of this expression. But for the ladder problem, these expressions were functions of time, so taking the derivative has a clear meaning: it''s the rate at which this expression changes as time change.

What makes the circle situation strange is that rather than saying a small amount of time dt has passed, which causes x and y to change, the derivative has the tiny nudges dx and dy both just floating free, not tied to some other common variable like time.

Let me show you how you can think about this: Give this expression x2+y2x^2 + y^2x2+y2 a name, maybe S. S is essentially a function of two variables; it takes every point (x,y)(x, y)(x,y) on the plane and associates it with a number.

For points on this circle, that number happens to be 25.

If you step off that circle away from the center, that value would be bigger. For other points (x,y)(x, y)(x,y) closer to the origin, that value is smaller.

What it means to take a derivative of this expression, a derivative of S, is to consider a tiny change to both these variables, some tiny change dx to x, and some tiny change dy to y – and not necessarily one that keeps you on this circle, by the way, it''s just some tiny step in any direction on the xy-plane.

From there you ask how much the value of S changes. That difference in the value of S, from the original point to the nudged point, is what we would call "dS" or "the change to the function S".

As we saw in previous lessons, these dx and dy expressions raised to the second power are neglible as the nudges approaches zero.

For example, say we''re starting at a point (3,4), and let''s just say that step dx is −0.02-0.02−0.02, and that dy is −0.01-0.01−0.01. Then the decrease to S, the amount that x2+y2x^2+y^2x2+y2 changes over that step, will be around 2(3)(−0.02)+2(4)(−0.01)2(3)(-0.02) + 2(4)(-0.01)2(3)(−0.02)+2(4)(−0.01).

That''s what this derivative expression 2x⋅dx+2y⋅dy2x \cdot dx + 2y \cdot dy2x⋅dx+2y⋅dy means. It tells you how much the value x2+y2x^2+y^2x2+y2 changes, as determined by the point (x,y)(x, y)(x,y) where you started, and the tiny step (dx,dy)(dx, dy)(dx,dy) that you take.

As with all things derivative, this is only an approximation, but it''s one that gets more and more true for smaller and smaller choices of dx and dy.

The key point is that when you restrict yourself to steps along this circle, you''re essentially saying you want to ensure that this value S doesn''t change; it starts at a value of 25, and you want to keep it at a value of 25; that is, dS should be 0. So setting this expression 2x⋅dx+2y⋅dy2x \cdot dx + 2y \cdot dy2x⋅dx+2y⋅dy equal to 0 is the condition under which a tiny step stays on the circle.

Again, this is only an approximation. Speaking more precisely, that condition keeps you on a tangent line of the circle, not the circle itself, but for tiny enough steps those are essentially the same thing.

Of course, there''s nothing special about the expression x2+y2=52x^2+y^2 = 5^2x2+y2=52 here. You could have some other expression involving x''s and y''s, representing some other curve, and taking the derivative of both sides like this would give you a way to relate dx to dy for tiny steps along that curve.

It''s always nice to think through more examples, so consider the expression sin⁡(x)⋅y2=x\sin(x) \cdot y^2 = xsin(x)⋅y2=x, which corresponds to many U-shaped curves on the plane. Those curves represent all the points (x,y)(x, y)(x,y) of the plane where the value of sin⁡(x)⋅y2\sin(x) \cdot y^2sin(x)⋅y2 equals the value of x.

Now imagine taking some tiny step with components (dx,dy)(dx, dy)(dx,dy), and not necessarily one that keeps you on the curve.

Taking the derivative of each side of the equation sin⁡(x)⋅y2=x\sin(x) \cdot y^2 = xsin(x)⋅y2=x will tell us how much the value of that side changes during this step. On the left side, the product rule tells us that this should be "left d-right plus right d-left": sin⁡(x)\sin(x)sin(x) times the change to y2y^2y2, which is 2y⋅dy2y \cdot dy2y⋅dy, plus y2y^2y2 times the change to sin⁡(x)\sin(x)sin(x), which is cos⁡(x)⋅dx\cos(x) \cdot dxcos(x)⋅dx. The right side is simply x, so the size of a change to the value is exactly dx, right? 1

Setting these two sides equal to each other is a way of saying "whatever your tiny step with coordinates (dx,dy)(dx, dy)(dx,dy) is, if it''s going to keep us on this curve, the values of both the left-hand side and the right-hand side must change by the same amount." That''s the only way this top equation can remain true.

From there, depending on what problem you''re solving, you could manipulate further with algebra, where perhaps the most common goal is to find dy divided by dx.

As one more example, let me show how you can use this technique to help find new derivative formulas. I''ve mentioned in a previous lesson that the derivative of exe^xex is itself, but what about the derivative of its inverse function the natural log of x?

The graph of ln⁡(x)\ln(x)ln(x) can be thought of as an implicit curve; all the points on the xy-plane where y=ln⁡(x)y = \ln(x)y=ln(x), it just happens to be the case that the x''s and y''s of this equation aren''t as intermingled as they were in other examples. The slope of this graph, dy/dx, should be the derivative of ln⁡(x)\ln(x)ln(x), right?

Well, to find that, first rearrange this equation y=ln⁡(x)y = \ln(x)y=ln(x) to be ey=xe^y = xey=x. This is exactly what the natural log of x means; it''s saying "e to the what equals x?"

Graphically this should make sense too, since you can draw a diagonal line and reflect the functions to see that they are inverses of eachother.

Since we know the derivative of eye^yey, we can take the derivative of both sides of this equation y=ln⁡(x)y = \ln(x)y=ln(x), effectively asking how a tiny step with components (dx,dy)(dx, dy)(dx,dy) changes the value of each side.

To ensure the step stays on the curve, the change to the left side of the equation, which is ey⋅dye^y \cdot dyey⋅dy, must equal the change to the right side, which is dx.

Rearranging, this means dy/dx, the slope of our graph, equals 1/ey1/e^y1/ey.

And when we''re on this curve, eye^yey is by definition the same as x, so evidently the slope is 1/x.

An expression for the slope of the graph of a function in terms of x like this is the derivative of that function, so the derivative of ln⁡(x)\ln(x)ln(x) is 1/x.

What is the slope of the tangent line at the point (1,2)(1, \sqrt{2})(1,2​) for the implicit curve defined by the equation y2−x2=1y^2 - x^2 = 1y2−x2=1? Compare this with the derivative at the point x=1 for the function f(x)=1+x2f(x) = \sqrt{1 + x^2}f(x)=1+x2​.

12\frac{1}{\sqrt{2}}2​1​

12\frac{1}{2}21​

By the way, all of this is a little peek into multivariable calculus, where you consider functions with multiple inputs, and how they change as you tweak those inputs. The key, as always, is to have a clear image in your head of what tiny nudges are at play, and how exactly they depend on each other.

Next up, I''l talk about about what exactly a limit is, and how it''s used to formalize the idea of a derivative.

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
