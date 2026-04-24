-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:24
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: The other way to visualize derivatives
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'The other way to visualize derivatives',
    'Picture yourself as a calculus student, about to begin your first course. The months ahead of you hold within them a lot of hard work, neat examples, not-so-neat examples, beautiful connections to physics, not-so-beautiful piles of formulas to memorize, plenty of moments getting stuck and banging your head into a wall, a few “aha” moments sprinkled in as well, and some truly lovely graphical intuitions to help guide you throughout it all.

ddx(x)\frac{d}{dx} (x^x)dxd​(x)

A graphical depiction of a partial differential equation.

But if the course ahead of you is anything like my first introduction to calculus, or any of the first courses I''ve seen in the years since, there''s one topic you will not see, but which I believe stands to greatly accelerate your learning.

You see, almost of all the visual intuitions for a first year in calculus are based on graphs. The derivative is the slope of a graph, the integral is an area under that graph, etc.

But as you generalize calculus beyond functions whose inputs and output are simply numbers, it''s not always possible to graph the function your analyzing.

If your intuitions for fundamental ideas like the derivative are rooted too rigidly in graphs, this can make for a very tall and largely unnecessary conceptual hurdle between you and the more “advanced” topics like multivariable calculus, complex analysis, differential geometry, etc.

What I want to share with you is a way of thinking about derivatives, which I''l refer to as the transformational view, which generalizes more seamlessly into some of the more general contexts where calculus shows up. And then we''l use it to analyze a certain fun puzzle about repeated fractions.

First off, let''s just make sure we''re all on the same page about what the standard visual is. If you graph a function which simply takes real numbers as inputs and outputs, one of the first things you learn in a calculus course is that the derivative gives you the slope of this graph.

What we mean by that is that the derivative of the function is a new function which for every input x, returns this slope.

Quick sanity check! What''s the derivative of sin⁡(x)x2\sin (x) x^2sin(x)x2?

2sin⁡(x)2\sin(x)^22sin(x)2

cos⁡(x)+2x\cos(x) + 2xcos(x)+2x

x2sin⁡(x)+2xsin⁡(x)x^2 \sin(x) + 2x \sin(x)x2sin(x)+2xsin(x)

x2cos⁡(x)+2xsin⁡(x)x^2 \cos(x) + 2x \sin(x)x2cos(x)+2xsin(x)

I''d encourage you not think of this derivative-as-slope idea as being the definition of the derivative; instead think of the derivative as asking how sensitive is the function to tiny nudges around a given input. The slope of the graph is just one way to think about that sensitivity, relevant only to this particular way of viewing functions.

The basic idea behind the alternate visual for the derivative is to think of the function as mapping all possible inputs on a number line to their corresponding outputs on a different number line.

In this case the derivative gives a measure of how much this input space gets stretched or squished in various regions. That is, if you zoom in around some specific input, and look at some evenly spaced points around it, the derivative of the function at that input will tell you how spread out or contracted those points become.

A specific example helps. The function f(x)=x2f(x) = x^2f(x)=x2 maps the point at 1 to 1, the point at 2 to 4, the point at 3 to 9, and so on.

If you were to zoom in on a little cluster of points around 1, and see where they land around the relevant output, which for this function happens to also be 1, you''d notice they get stretched out.

In fact, it roughly looks like stretching out by a factor of 2. And the closer you zoom in, the more this local behavior just looks like multiplying by a factor of 2. This is what it means for the derivative of x2x^2x2 at x=1 to be 2; it''s what that fact looks like in the context of transformations.

If you looked at a neighborhood of points around the input 3, they would roughly get stretched out by a factor of 6, which is what it means for the derivative of this function at the input 3 to be 6.

Around the point x=14x=\frac{1}{4}x=41​, what factor would the surrounding points stretch out by?

12\frac{1}{2}21​

14\frac{1}{4}41​

18\frac{1}{8}81​

116\frac{1}{16}161​

The input zero is interesting: Zooming in by a factor of 10, it doesn''t exactly look like a constant stretching or squishing.

For one thing, all the outputs end up on the right positive side of things. As we zoom in closer, by 100x, or 1,0x, it looks more and more like the small neighborhood around zero gets collapsed to a point.

This is what it looks like for a derivative to be 0, the local behavior looks more and more like multiplying a number line by 0. It doesn''t have to completely collapse things to a point at any particular zoom level; instead it''s a matter of what the limiting behavior is here.

If you also considered negative inputs, things start to feel a little cramped, since these collide with all those positive input values. This is one downside of thinking of functions transformations. But for derivatives, we only care about local behavior anyway; that is, what happens in a small range around a given input.

We really only care about local behavior here.

Notice that the inputs in a little neighborhood around, say, −2-2−2, don''t just get stretch out, they also get flipped around.

Specifically, the action on such a neighborhood looks more and more like multiplying by −4-4−4 the closer you zoom in. This is what it looks like for a function to have a negative derivative.

Given this interpretation of the derivative, can you come up with a formula for it using expressions you''re already familiar with?

f′(x)=dx⋅dyf''(x) = dx \cdot dyf′(x)=dx⋅dy

dy=f′(x)⋅dxdy = f''(x) \cdot dxdy=f′(x)⋅dx

f′(x)=dyf''(x) = dyf′(x)=dy

f′(x)=dxf''(x) = dxf′(x)=dx

This is all well and good, but let''s see how it''s useful in solving a problem.

A friend of mine recently asked me a pretty fun question about this infinite fraction:

Maybe you''ve seen this before, but my friend''s questions cuts to something you may not have thought about before, relevant to the view of the derivative we''re looking at here.

A typical way you might evaluate an expression like this is to first set it equal to x.

Then, notice that there is a copy of the full fraction inside itself, giving us this:

You can also think of this as finding a fixed point of the function f(x)=1+1xf(x) = 1 + \frac{1}{x}f(x)=1+x1​. But here''s the thing, there are actually two solutions for x; two special number where 1 plus 1 over that number gives you back the same number.

One is the golden ratio φ\varphiφ, around 1.618., and the other is at −0.618.-0.618.−0.618., which is −1φ\frac{-1}{\varphi}φ−1​. I like to call this other number φ\varphiφ''s "little brother", since just about any property φ\varphiφ has, this number also has. So would it be valid to say that this infinite fraction also equals φ\varphiφ''s little brother, −0.618.-0.618.−0.618.?

Which one is the answer?

Maybe you initially say “obviously not!”. Everything on the left-hand side is positive, so how could it equal a negative number? Well, first let''s be clear about what we mean by an expression like this.

One way to think of it, though it''s not the only way, is to imagine starting with some constant, like 1, then repeatedly applying the function 1+1x1 + \frac{1}{x}1+x1​, and asking about what this approaches as you keep going.

So in that view, maybe if you start with a negative number, it''s not so crazy for the whole expression to end up negative.

After all, if you start with −1φ\frac{-1}{\varphi}φ−1​, then apply the function 1+1x1 + \frac{1}{x}1+x1​, you get back −1φ\frac{-1}{\varphi}φ−1​. So no matter how many times you apply it, you''l stay fixed at that value.

Well, even then, there is one reason you view φ\varphiφ as the favorite brother in this pair. Try this, pull up a calculator of some kind, start with any random number, then plug it into this function 1+1x1 + \frac{1}{x}1+x1​. Then do it again. And again. And again and again and again.

No matter what constant you start with, you will eventually end up at 1.618. Even if you start with a negative number, even one really really close φ\varphiφ''s little brother, eventually it will shy away from that value and jump back to phi.

So what''s going on here? Why is one of these fixed points favored over the other?

Maybe you can already see how the transformational understanding of derivatives will help us to understand this setup, but for the sake of having a point of contrast, let me show how a problem like this is often taught using graphs.

If you plug in some random input to this function, the y-value tells you the corresponding output.

To think about plugging that output back into the function, you might first move horizontally until you hit the line y=x, which will get you to a position where the x-value corresponds to your previous output.

Then, you can move vertically to see what the output at this new x-value is.

Then repeat: Move horizontally to the line y=x to find a point whose x-value is the same as the output you just got, then move vertically to apply the function.

Personally, I think this is kind of an awkward way to think about repeatedly applying a function, don''t you? I mean, it makes sense, but you kind of have to pause to think about it to remember which way to draw the lines. You can, if you want, think through what conditions make this spider-web process narrow in on a fixed point, vs. propagating away from it.

When the slope of the graph at the point where it intersects the line x=y is relatively shallow, specifically with an absolute value less than 1, then the lines from this cobweb diagram will spiral in closer and closer to that point. But when this slope has an absolute value greater than 1, each new line drawn tends to go away from that intersection point.

But let''s turn to how this function acts as a transformation.

What does it look like to repeatedly apply the 1+1x1 + \frac{1}{x}1+x1​ in this context? Well, after letting it map all input points to outputs, you consider those as the new inputs and apply that same process again and again.

Notice how in animating this with these dots representing some sample points, it doesn''t take many iterations at all for most dots to clump in around 1.618.

Now, we know that 1.618 and its little brother −0.618-0.618−0.618 stay fixed in place during each iteration of this process, but zoom in on the neighborhood around 1.618.

During the map, points in that region get contracted around φ\varphiφ, meaning the function 1+1x1 + \frac{1}{x}1+x1​ has a derivative with magnitude less than 1 at the input φ\varphiφ. In fact, this derivative works out to be around −0.38-0.38−0.38 at the input φ\varphiφ. So each repeated application scrunches that neighborhood smaller, like a gravitational pull towards φ\varphiφ.

So now tell me what you think happens in a neighborhood of φ\varphiφ''s little brother.

There, the derivative has a magnitude larger than 1, so points near this fixed point are repelled away from it, by more than a factor of 2 each iteration. Mathematicians would call this right value a “stable” fixed point, while the left one is an “unstable fixed point”. Something is considered stable if when you perturb it a little, it tends to come back towards where it started, rather than going away.

So the stability of a fixed point is determined by whether the magnitude of its derivative is bigger or smaller than 1, and this explains why φ\varphiφ always shows up in the numerical play, and φ\varphiφ''s little brother does not. As to whether or not you want to consider φ\varphiφ''s little brother a valid value of our infinite fraction? Well, that''s really up to you. Everything we just showed suggests that if you think of this expression as representing a limiting process, only phi makes sense. But maybe you think of it not as a limit, but as a static, purely algebraic object.

Either way, math is cool!

My point here not that understanding derivatives as measuring changes in density like this is somehow better than the graphical view on the whole. In fact, picturing an entire function this way, as opposed to a small window, can be clunky and impractical as compared to graphs. My point is that it deserves more of a mention in most introductory calculus courses, and can help make a student''s understanding of the derivative a little more flexible.

But as I mentioned, the real reason I''d recommend you carry this perspective with you as you learn new topics is not so much for how it will strengthen your understanding of single-variable calculus, but for what comes after. There are many topics typically taught in a college math department, which don''t exactly have a reputation of being incredibly accessible.

Understanding the transformation view of derivatives provides a great pathway into topics such as multivariable calculus, complex analysis and differential geometry.

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
