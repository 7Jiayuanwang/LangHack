-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:09
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: Visualizing the chain rule and product rule
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'Visualizing the chain rule and product rule',
    'In the last videos I talked about the derivatives of simple functions, things like powers of x, sin⁡(x)\sin(x)sin(x), and exponentials, the goal being to have a clear picture or intuition to hold in your mind that explains where these formulas come from.

Most functions you use to model the world involve mixing, combining and tweaking these simple functions in some way; so our goal now is to understand how to take derivatives of more complicated combinations; where again, I want you to have a clear picture in mind for each rule.

This really boils down into three basic ways to combine functions together: Adding them, multiplying them, and putting one inside the other; also known as composing them. Sure, you could say subtracting them, but that’s really just multiplying the second by −1-1−1, then adding. Likewise, dividing functions is really just the same as plugging one into the function 1/x, then multiplying.

Most functions you come across just involve layering on these three types of combinations, with no bound on how monstrous things can become. But as long as you know how derivatives play with those three types of combinations, you can always just take it step by step and peel through the layers.

So, the question is, if you know the derivatives of two functions, what is the derivative of their sum, of their product, and of the function compositions between them?

Derivative Sum Rule for the function f(x)=g(x)+h(x)f(x) = g(x) + h(x)f(x)=g(x)+h(x)

The sum rule is the easiest, if somewhat tongue-twisting to say out loud: The derivative of a sum of two functions is the sum of their derivatives. But it’s worth warming up with an example and really thinking through what it means to take a derivative of a sum of two functions, since the derivative patterns for products and function composition won’t be so straight forward, and will require this kind of deeper thinking.

For example, let''s think about this function f(x)=sin⁡(x)+x2f(x) = \sin(x) + x^2f(x)=sin(x)+x2. It''s a function where, for every input, you add together the values of sin⁡(x)\sin(x)sin(x) and x2x^2x2 at that point.

Given the input x=0.5x = 0.5x=0.5, the output of the function is the height of the sine graph represented by the blue bar plus the height of the x2x^2x2 parabola represented by the green bar.

For the derivative, you ask what happens as you nudge the input slightly, maybe increasing it to 0.5+dx0.5 + dx0.5+dx. The difference in the value of f between these two values is what we call df.

Well, pictured like this, I think you’l agree that the total change in height is whatever the change to the sine graph is, what we might call d(sin⁡(x))d\left(\sin(x)\right)d(sin(x)), plus whatever the change to x2x^2x2 is, d(x2)d(x^2)d(x2).

This gives us df as the sum of change of the two functions.

We know the derivative of sine is cosine, and what that means is that this little change d(sin⁡(x))d\left(\sin(x)\right)d(sin(x)) would be about cos⁡(x)⋅dx\cos(x) \cdot dxcos(x)⋅dx. It’s proportional to the size of dx, with a proportionality constant equal to cosine of whatever input we started at. Similarly, because the derivative of x2x^2x2 is 2x, the change in the height of the x2x^2x2 graph is about 2x⋅dx2x \cdot dx2x⋅dx.

So, dfdx\frac{df}{dx}dxdf​, the ratio of the tiny change to the sum function to the tiny change in x that caused it, is indeed cos⁡(x)+2x\cos(x)+2xcos(x)+2x, the sum of the derivatives of its parts.

What is the derivative of the function f(x)=x4+cos⁡(x)f(x) = x^4 + \cos(x)f(x)=x4+cos(x)?

4x3−sin⁡(x)4x^3 - \sin(x)4x3−sin(x)

4x3+sin⁡(x)4x^3 + \sin(x)4x3+sin(x)

Things are a bit different for the product of two functions. Let’s think through why, in terms of tiny nudges. In this case, I don’t think graphs are our best bet for visualizing things. Pretty commonly in math, all levels of math really, if you’re dealing with a product of two things, it helps to try to understand it as some form of area.

For example, for the function sin⁡(x)⋅x2\sin(x) \cdot x^2sin(x)⋅x2, you might try to configure some mental setup of a box whose side-lengths are sin⁡(x)\sin(x)sin(x) and x2x^2x2.

What would that mean? Well, since these are functions, you might think of these sides as adjustable; dependent on the value of x, which you might think of as a number that you can freely adjust.

So, just getting the feel for this, focus on that top side, whose changes as the function sin⁡(x)\sin(x)sin(x). As you change the value of x up from 0, it increases up to a length of 1 as sin⁡(x)\sin(x)sin(x) moves towards its peak. After that, it starts decreasing as sin⁡(x)\sin(x)sin(x) comes down from 1. And likewise, that height changes as x2x^2x2.

So f(x), defined as this product, is the area of this box. For the derivative, think about how a tiny change to x by dx influences this area; that resulting change in area is df. That nudge to x causes the width to change by some small d(sin⁡(x))d\left( \sin(x)\right)d(sin(x)), and the height to change by some d(x2)d(x^2)d(x2).

This gives us three little snippets of new area: A thin rectangle on the bottom, whose area is its width, sin⁡(x)\sin(x)sin(x), times its thin height, d(x2)d(x^2)d(x2); there’s a thin rectangle on the right, whose area is its height, x2x^2x2, times its thin width, d(sin⁡(x))d\left( \sin(x)\right)d(sin(x)). And there’s also a bit in the corner. But we can ignore it, since its area will ultimately be proportional to dx2dx^2dx2, which becomes negligible as dx goes to 0.

This is very similar to what I showed in the last chapter, with the x2x^2x2 diagram. Just like then, keep in mind that I’m using somewhat beefy changes to draw things, so we can see them, but in principle think of dx as very small, meaning d(x2)d(x^2)d(x2) and d(sin⁡(x))d\left(\sin(x)\right)d(sin(x)) are also very small.

So we are interested in finding the change to the area of this rectangle represented by the two smaller rectangles highlighted in yellow.

Applying what we know about the derivative of sine and x2x^2x2, that tiny change d(x2)d(x^2)d(x2) is 2x⋅dx2x \cdot dx2x⋅dx, and that tiny change d(sin⁡(x))d\left(\sin(x)\right)d(sin(x)) is cos⁡(x)dx\cos(x)dxcos(x)dx.

Dividing out by that dx, the derivative dfdx\frac{df}{dx}dxdf​ is sin⁡(x)\sin(x)sin(x) by the derivative of x2x^2x2, plus x2x^2x2 by the derivative of sine.

This line of reasoning works for any two functions.

Generic Product Rule where f(x)=g(x)h(x)f(x) = g(x)h(x)f(x)=g(x)h(x).

A common mnemonic for the product rule is to say in your head "left d right, right d left". In this example, sin⁡(x)⋅x2\sin(x) \cdot x^2sin(x)⋅x2, "left d right" means you take the left function, in this case g(x)=sin⁡(x)g(x) = \sin(x)g(x)=sin(x), times the derivative of the right, h(x)=x2h(x) = x^2h(x)=x2, which gives 2x. Then you add "right d left": the right function, x2x^2x2, times the derivative of the left, cos⁡(x)\cos(x)cos(x).

Out of context, this feels like kind of a strange rule, but when you think of this adjustable box you can actually see how those terms represent slivers of area. "Left d right" is the area of this bottom rectangle, and “right d left” is the area of this rectangle on the right.

By the way, I should mention that if you multiply by a constant, say 2⋅sin⁡(x)2 \cdot \sin(x)2⋅sin(x), things end up much simpler. The derivative is just that same constant times the derivative of the function, in this case 2⋅cos⁡(x)2 \cdot \cos(x)2⋅cos(x). I’l leave it to you to pause and ponder to verify that this makes sense.

Aside from addition and multiplication, the other common way to combine functions that comes up all the time is function composition. For example, let’s say we take the function x2x^2x2, and shove it inside sin⁡(x)\sin(x)sin(x) to get a new function, sin⁡(x2)\sin(x^2)sin(x2). Or, in other words, the output of the function x2x^2x2 gets fed as input to sine function.

What’s the derivative of this new function?

Here I’l choose yet another way to visualize things, just to emphasize that in creative math, we have lots of options. I’l put up three number lines. The top one will hold the value of x, the second one will represent the value of x2x^2x2, and the third line will hold the value of sin⁡(x2)\sin(x^2)sin(x2).

That is, the function x2x^2x2 gets you from line 1 to line 2, and the function sine gets you from line 2 to line 3. In the image, I''m showing an x value of 0.5 on the first number line. So the second number line, which just displays x2x^2x2, is showing the ouput of the inner function, 0.25. The third number line shows sin⁡(x2)\sin(x^2)sin(x2), which is really just the sine of the previous value, so sin⁡(0.25)≈0.247\sin(0.25) \approx 0.247sin(0.25)≈0.247.

What is the value of the composed function given the input x=2? To get a hang of this visualization technique, go from the first line to the second line to the third line.

sin⁡(2)≈0.909\sin(2) \approx 0.909sin(2)≈0.909

sin⁡(4)≈−0.757\sin(4) \approx -0.757sin(4)≈−0.757

As I shift that value of x, maybe up to the value 3, then the value on the second shifts to whatever x2x^2x2 is, in this case 9. And that bottom value, being the sin⁡(x2)\sin(x^2)sin(x2), will go over to whatever sin⁡(9)\sin(9)sin(9) is.

So for the derivative, let’s again think of nudging that x-value by some little dx. I find it helpful to imagine x starting out as some actual number, maybe 1.5, and dx as some small number approaching zero, like 0.1.

The resulting nudge to this second value, the change to x2x^2x2 caused by such a dx, is what we might call d(x2)d(x^2)d(x2). You can expand this as 2x⋅dx2x \cdot dx2x⋅dx. For our specific input that length would be 2(1.5)dx, but it helps to keep it written as d(x2)d(x^2)d(x2) for now. In fact let me go one step further and give a new name to x2x^2x2, maybe h, so this nudge d(x2)d(x^2)d(x2) is just dh.

The fact that sin⁡(h)\sin(h)sin(h) is moving left while the dh bump is to the right just means that this change d(sin⁡(h))d\left(\sin(h)\right)d(sin(h)) is some negative number.

Now think of that third value, which is pegged at sin⁡(h)\sin(h)sin(h). Its change is d(sin⁡(h))d\left(\sin(h)\right)d(sin(h)), the tiny change caused by the nudge dh. Well, we know the derivative of sine, so we can expand d(sin⁡(h))d\left(\sin(h)\right)d(sin(h)) as cos⁡(h)⋅dh\cos(h) \cdot dhcos(h)⋅dh; that’s what it means for the derivative of sine to be cosine.

The derivative of f(x)=sin⁡(x2)f(x) = \sin(x^2)f(x)=sin(x2) is reframed by using the transformation h=x2h=x^2h=x2.

Now we can unfold the transformation, replacing h with x2x^2x2 and dh with d(x2)d(x^2)d(x2). So the bottom nudge becomes cos⁡(x2)d(x2)\cos(x^2)d(x^2)cos(x2)d(x2) and the middle nudge becomes d(x2)d(x^2)d(x2). Of course, we also know that d(x2)=2x⋅dxd(x^2) = 2x \cdot dxd(x2)=2x⋅dx and so we can substitute that into the diagram as well.

It’s always good to remind yourself of what this all actually means. In this case where we started at x=1.5x = 1.5x=1.5 up top, this means that the size of that nudge on the third line is about cos⁡(1.52)⋅2(1.5)\cos(1.5^2) \cdot 2(1.5)cos(1.52)⋅2(1.5) multiplied by the size of dx; proportional to the size of dx, where the derivative here gives us that proportionality constant.

Since the nudge on the third line represents the change to our initial function df when we introduced the small nudge dx, we can rearrange the expression and this gives us the derivative of the function.

Notice what we have here, we have the derivative of the outside function, still taking in the unaltered inside function, and we multiply it by the derivative of the inside function.

Again, there’s nothing special about sin⁡(x)\sin(x)sin(x) and x2x^2x2. If you have two functions g(x) and h(x), the derivative of their composition function g(h(x))g\left(h(x)\right)g(h(x)) is the derivative of g, evaluated at h(x), times the derivative of h. This is what we call the “chain rule”.

The chain rule for the function f(x)=g(h(x))f(x) = g(h(x))f(x)=g(h(x))

Notice, for the derivative of g, I’m writing it as ddh\frac{d}{dh}dhd​ instead of ddx\frac{d}{dx}dxd​. On the symbolic level, this serves as a reminder that you still plug in the inner function to this derivative. But it’s also an important reflection of what this derivative of the outer function actually represents.

Remember, in our three-lines setup, when we took the derivative of sine on the bottom, we expanded the size of the nudge d(sin⁡)d(\sin)d(sin) as cos⁡(h)⋅dh\cos(h) \cdot dhcos(h)⋅dh. This was because we didn’t immediately know how the size of that bottom nudge depended on x, that’s kind of the whole thing we’re trying to figure out, but we could take the derivative with respect to the intermediate variable h. That is, figure out how to express the size of that nudge as multiple of dh. Then it unfolded by figuring out what dh was.

So in this chain rule expression we’re saying to look at the ratio between the tiny change in g and the tiny change in h that caused it, where h is the value that we’re plugging into g. Then multiply that by the tiny change in h divided by the tiny change in x that caused it.

The dh’s cancel to give the ratio between a tiny change in the final output, and the tiny change to the input that, through a certain chain of events, brought it about. That cancellation of dh is more than just a notational trick, it’s a genuine reflection of the tiny nudges that underpin calculus.

So those are the three basic tools in your belt to handle derivatives of functions that combine many smaller things: The sum rule, the product rule and the chain rule. I should say, there’s a big difference between knowing what the chain rule and product rules are, and being fluent with applying them in even the most hairy of situations.

I said this at the start of the series, but it’s worth repeating: Watching and reading about the mechanics of calculus will never substitute for practicing them yourself, and building the muscles to do these computations yourself. I wish I could offer to do that for you, but I’m afraid the ball is in your court, my friend, to seek out practice.

What I can offer, and what I hope I have offered, is to show you where these rules come from, to show that they’re not just something to be memorized and hammered away; but instead are natural patterns that you too could have discovered by just patiently thinking through what a derivative means.

As a fun exercise, think about the derivative of sin⁡(x)2\sin(x)^2sin(x)2. First, use the chain rule, thinking of this as shoving the function sin⁡(x)\sin(x)sin(x) into the function x2x^2x2, then taking the derivative of the outside multiplied by the derivative of the inside.

Then, think of it using the product rule, interpreting it as sin⁡(x)⋅sin⁡(x)\sin(x) \cdot \sin(x)sin(x)⋅sin(x), and think about how this relates to the visual for the derivative of x2x^2x2 shown in the last video. That should give a deeper feel for the chain rule.

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
