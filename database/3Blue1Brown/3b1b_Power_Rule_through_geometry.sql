-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:06
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: Power Rule through geometry
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'Power Rule through geometry',
    'After introducing the derivative and its relation to rates of change in the last lesson, the next step is to learn how to compute the derivatives of functions that are explicitly given with some formula. A typical calculus student spends quite a bit of time drilling on how to compute derivatives, often without the context of a concrete rate of change problem. For example, a worksheet may ask you to compute the derivative of this function:

But there may be no indication of what physical process this function describes, or what significance its rate of change has. That''s not necessarily a bad thing, it''s analogous to how we often learn the multiplication table by drilling on many facts like 7×6=427 \times 6 = 427×6=42 without perpetually having to put each such equation in context.

Still, before diving in, it may be worth emphasizing why all these formulas are worth learning in the first place, and why exercises asking students to drill on them are worth the effort. We model many real-world phenomena, especially those in physics, with polynomials, trigonometric functions, exponentials, and the like, so building up a fluency with actually computing derivatives of these functions gives you a language to readily understand the rates of change for these phenomena.

Think of how knowing the multiplication table by heart frees up a student to think about more complicated ideas in arithmetic and algebra.

So in this chapter and the next, the aim is to show how you can think about a few of these rules intuitively and geometrically; and I really want to encourage you to never forget about the tiny nudges at the heart of derivatives.

All that said, even if there''s value to drilling on these formulas and memorizing them, the goal of this series is for you to feel like these are facts you could have discovered yourself. So for the next few chapters, let''s put ourselves in a mindset of patience and discovery. Discovering these formulas can be a beautiful exercise in creativity, requiring you to sniff out how tiny changes to one quantity influence tiny changes to another.

Even if you don''t need to think through these derivations every time you compute a derivative, going through them can reinforce the core idea of what derivatives are all about.

Let’s start with a function like f(x)=x2f(x) = x^2f(x)=x2. What is its derivative? That is to say, if you look at some value of x, like x=2x = 2x=2, and compare it to a value slightly bigger, just dx bigger, what’s that corresponding change in the value of the function, df?

In particular, what is df divided by dx? The rate at which this function changes per unit change in x? As the first step for intuition, we know you can think of the ratio dfdx\frac{df}{dx}dxdf​ as the slope of a tangent line to the graph of x2x^2x2. From that, we can see that the slope generally increases as x increases. At 0, the tangent line is flat, so the slope is 0. At x=1x = 1x=1, it’s something steeper, at x=2, it’s steeper still.

But looking at graphs isn’t generally the best way to understand the precise formula for a derivative. For that, it’s best to take a more literal look at what x2x^2x2 actually means. In this case, let’s picture a square whose side length is x.

If you increase x by a tiny nudge dx, what is the resulting change to the area of the square? That slight change in area represents df: the tiny increase in the value of f(x)=x2f(x)=x^2f(x)=x2 caused by increasing x by a tiny nudge dx.

If x=2 and dx was 0.01, approximately how much bigger would the new area be relative to the size of dx?

That little square has area dx2dx^2dx2, but you should think of this as being really tiny; negligibly tiny. For example, if dx was 0.01, it would be 0.01. I’m drawing dx with a fair bit of width here, so we can see it, but always remember in principle dx should be thought of as a truly tiny amount.

Phrased more precisely, our final consideration will always be what happens as the size of this dx approaches 0, and as that happens the proportion of this yellow area df which is accounted for by the tiny dx2dx^2dx2 corner will go to 0.

The full unapproximated change df represented by all the yellow area above looks like df=2x⋅dx+(dx)2df = 2x \cdot dx + (dx)^2df=2x⋅dx+(dx)2. So you might begin thinking of the expression for the derivative like this:

Remember, if we''re using this "d" notation, the implicit meaning is that we consider what happens as dx→0dx \to 0dx→0. So in this case, our final expression would look like this.

Notice how we could have simply ignored the (dx)2(dx)^2(dx)2 term since it doesn''t get fully canceled out when dividing by dx. A good rule of thumb is that you can ignore anything which includes a dx raised to a power greater than one; that is, a tiny change squared is a negligible change.

For example, if you were starting at x=3, as you slightly increase x, what is the rate in change of the area per unit of length added?

The rate of change in area would be 6.

The rate of change in area would be 3.

The rate of change in area would be 4.

The rate of change in area would be 2.

Let’s try a different simple function, f(x)=x3f(x) = x^3f(x)=x3. This will be the geometric view of what you and I went through algebraically in the last chapter for the function x3x^3x3. We can think of x3x^3x3 geometrically as the volume of a cube with side lengths x.

When you increase x by a tiny nudge, dx, the volume increases as shown in the figure below. That represents all the volume in a cube with side length x+dxx + dxx+dx that’s not already in the original cube with side length x.

Remember that we are interested in what happens as dx approaches 0. The length of dx is illustrated so big to demonstrate the change in volume it introduces.

This figure shows the increase in volume of a cube when its side length x is increased by a small nudge dx. It’s nice to think of this new volume broken up into multiple components, but almost all of it comes from the three square faces; Or, said a little more precisely, as dx approaches 0, those three squares comprise a portion closer and closer to 100% of the new volume.

Each of those thin squares has a volume of x2⋅dxx^2 \cdot dxx2⋅dx; the area of the face times the thickness of dx, so in total this gives us 3x2dx3x^2dx3x2dx of volume change. There are some other slivers of volume along the edges, and in the corner, but their volume will be proportional to dx2dx^2dx2, or dx3dx^3dx3, so they can be ignored. Again, this is because ultimately they will be divided by dx, and if there’s still any dx remaining, these terms won’t survive the process of letting dx approach 0.

So the derivative of x3x^3x3, the rate at which x3x^3x3 changes per unit of change in x, is 3x23x^23x2. Looking at the graph, this means the slope of the graph of x3x^3x3 at each point x is exactly 3x23x^23x2.

Graphical intuition with slope can tell us why this derivative is high on the left, 0 at the origin, and high on the right, but just thinking in terms of graphs would not land us on the precise quantity 3x23x^23x2. For that, we had to take a much more direct look at the actual meaning of the function.

In practice, you wouldn’t necessarily think of the square every time you’re taking a derivative of x2x^2x2, nor would you necessarily think of the cube when taking a derivative of x3x^3x3. Instead, thinking like a mathematician, can you generalize this approach to see if a pattern emerges? Can you invent a tool to find the derivative of any polynomial?

Let''s look at the pattern of the first three monomial functions, where I''ve included the monomial of degree one for completeness. So far, each of these monomial functions has had a nice geometric meaning. Nudging the input x by a small amount dx has allowed us to see how the geometry changes and find the derivative.

However, from a geometric perspective, we have hit a roadblock. How do we visualize four dimensions? One path forward is to continue with algebra, using our geometric intuition to inform the base cases. For example, we can expand higher degree monomial expressions, where the input x have been nudged by a small amount dx. This gives us the following series of expressions.

Focusing on the rate in change introduced by the very small nudge, highlighted in red, and ignoring expressions containing dx2dx^2dx2, the pattern that emerges is what''s known in the business as the "power rule". Given a monomial function raised to some power, n, applying the rule gives us the derivative of the function.

Power Rule Definition

Even though in practice you will find yourself performing this derivative quickly and symbolically, imagining that exponent hopping down to the front, every now and then it’s nice to step back and remember why this rule works. Not just because it’s pretty, and not just because it helps to remind us that math actually makes sense and isn’t just a pile of formulas to memorize, but because it flexes that very important muscle of thinking about derivatives in terms of tiny nudges.

What is the derivative of the function f(x)=3x2f(x)= 3x^2f(x)=3x2?

As another example, think of the function f(x)=1xf(x) = \frac{1}{x}f(x)=x1​. Now, on the one hand, you could blindly try applying the power rule, since 1x\frac{1}{x}x1​ is the same as writing x−1x^{-1}x−1. That would involve letting that −1-1−1 hop down to become a coefficient, leaving behind one less than itself in the exponent, −2-2−2. But let’s have some fun and see if we can reason this geometrically, rather than just plugging it through a formula.

The value 1/x is asking “what number multiplied by x equals 1”, so here’s how I’d visualize it: Imagine a little rectangular puddle of water in two dimensions with area 1. Let’s say that it’s width is x, which means its height must be 1/x, since the total area is 1.

For example, if you increase x to 3, the other side must be squished down to 13\frac{1}{3}31​. And if x=2, the other side is forced to be 12\frac{1}{2}21​.

This is a nice way to think about the graph of 1/x, by the way. If you think of the width x of this puddle in the xy-plane, the corresponding output 1/x, the height of the graph above that point, is whatever height the puddle must have to maintain an area of 1.

For the derivative, imagine nudging the input x up by a value dx. How must the height of this rectangle change so that the area remains unchanged at 1? That is, increasing the width by dx adds some new area to the right here, so the puddle must decrease in height by some d(1/x) so that the area lost off the top here cancels that out.

You should think of that d(1/x) as being some tiny negative value, since it’s decreasing the height of this rectangle. And once you work out d(1/x)/dx, compare it to what happens if you apply the power rule purely symbolically to x−1x^{-1}x−1.

What is the derivative of the function f(x)=1xf(x) = \frac{1}{x}f(x)=x1​?

1x2\frac{1}{x^2}x21​

12x\frac{1}{2x}2x1​

-1x2\frac{1}{x^2}x21​

Here are a couple questions to test your knowledge of derivatives and the past chapters.

​See if you can reason your way through the derivative of f(x)=xf(x) = \sqrt{x}f(x)=x​ which also can be written as x12x^{\frac{1}{2}}x21​. By far the easiest way to compute this is to apply the power rule: The exponent hops down as a coefficient, leaving behind 12−1=−12\frac{1}{2} - 1 = -\frac{1}{2}21​−1=−21​ in the exponent.

But is this valid? And following our current playful and geometric spirit, is there a way to read what this really means?

Approaching this question with geometry is, by far, overkill. Frankly, it''s a bit of a mind warp. But it does offer a satisfying explanation of an otherwise mostly symbolic fact, and more than that it''s one more opportunity to flex our muscles in reasoning about how small nudges to one value can affect another.

Unlike in previous problems, dx does not represent geometric length, but instead represents area.

Using the diagram above, what is the derivative of the function f(x)=xf(x) = \sqrt{x}f(x)=x​?

12x\frac{1}{2\sqrt{x}}2x​1​

x2\frac{x}{\sqrt{2}}2​x​

12x\frac{1}{2}x21​x

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
