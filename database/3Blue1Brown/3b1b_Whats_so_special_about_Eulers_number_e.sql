-- ============================================================
-- LangHack 自定义文章数据
-- 生成时间: 2026-01-12 20:52:11
-- 文章数量: 1
-- ============================================================

USE english_learning;

-- 确保用户上传专用单元存在
INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 1, '用户上传内容', '用户自主上传的学习内容', 999, NOW(), NOW());

-- ============================================================
-- 插入文章数据
-- ============================================================

-- 文章 1: What's so special about Euler's number e?
INSERT INTO texts (
    unit_id, title, content, difficulty, tags,
    uploaded_by, is_public, status, view_count, like_count,
    order_num, deleted, created_time, updated_time
) VALUES (
    999,
    'What''s so special about Euler''s number e?',
    '- Francois le Lionnais

I’ve introduce a few derivative formulas, but a really important one I left out was exponentials. So here I want to talk about the derivatives of exponential functions like 2x2^x2x, 7x7^x7x, and to show why exe^xex is arguably the most important exponential.

First of all, to get an intuition, let’s focus on the exponential function 2x2^x2x.

And let’s think of that input as a time, t, in days, and the output 2t2^t2t as a population size, perhaps of a particularly fertile band of π\piπ creatures, which doubles every day.

Actually, instead of population size, which grows in discrete little jumps with each new baby π\piπ creature, maybe let’s think of 2t2^t2t as the total mass of the population, which better reflects the continuity of this function 2t2^t2t.

For example, at time t=0, the total mass 20=12^0=120=1, for the mass of one creature. At t=1t = 1t=1 day, the population has grown to 21=2^1 = 221=2 creatures masses. And day t=2t = 2t=2, it’s at 2=42^2 = 422=4, and in general it keeps doubling every day.

For the derivative, we want to find dMdt\frac{dM}{dt}dtdM​, the rate at which this population mass is growing, thought of as a tiny change to mass divided by a tiny change in time. Let’s start by thinking of the rate of change over a full day, say between day 2 and day 3. In that case, it grows from 4 to 8, so that’s four creatures masses added over the course of that day.

Notice, the rate of growth equals the population mass at the start of the day.

Between day 3 and day 4, the mass grows from 8 to 16, so that’s a rate of 8 creature masses per day, which again, is equal to the population size at the start of the day. And in general, the rate of growth over a full day equals the population size at the start of that day.

Examples of the rate of change between days.

So it might be tempting to say that this means the derivative of 2t2^t2t equals itself; that the rate of change of 2t2^t2t at a given time t equals the value of 2t2^t2t itself. On the day when 2t2^t2t is 8, the number of new creature-masses added per day is also 8; on the day when 2t2^t2t is 16, the number of new creature-masses added per day is also 16.

This is definitely in the right direction, but it’s not quite correct. What we’re doing here is making comparisons over the course of a full day, considering the difference between 2t+12^{t+1}2t+1 and 2t2^t2t. But for the derivative, we need to ask about what happens for smaller and smaller changes. What’s the growth rate over the course of a tenth of a day. a hundredth of a day. one one billionth of a day, etc.

This is why I had us think of the function as representing population mass vs. time, since it makes sense to ask about a tiny change in mass over a tiny fraction of a day, but it doesn’t make as much sense to ask about the tiny change in the discrete population size per second.

We know that the derivative of the exponential function M(t)=2tM(t) = 2^tM(t)=2t is close-to, but not quite equal-to itself, suggesting that the derivative of an exponential function is another exponential function. Looking at any point on the plot, will the actual derivative be smaller or larger than the output of the function?

The derivative at a point will be smaller than the output of the function.

The derivative at a point will be larger than the output of the function.

More abstractly, for some tiny change in time dt, we want to understand the difference between 2t+dt2^{t+dt}2t+dt and 2t2^t2t, all divided by dt. The change in the function per unit time, but now we’re looking very narrowly around a given point in time, rather than over the course of a full day.

Here’s the thing. I would love if there was some clear geometric picture that made what’s about to follow just pop out. Some diagram where you could point to one value and say "see that part, that is the derivative of 2t2^t2t". If you know of one, please, let me know1. And while the goal here, as with the rest of the series, is to maintain a spirit of playful discovery, the type of play that follows will have more to do with finding numerical patterns, rather than visual ones.

Start by taking a close look at the exponent term 2t+dt2^{t+dt}2t+dt in the numerator. A core property of exponentials is that you can break this up as 2t⋅2dt2^{t} \cdot 2^{dt}2t⋅2dt. That really is the most important property of exponents; if you add two values in the exponent, you can break up the output as a product of some kind. This is what lets you relate additive ideas, like tiny steps in time, to multiplicative ideas, like rates and ratios.

Applying this property gives us the expression below.

Notice how both expressions in the numerator contain the term 2t2^t2t, so we can factor out 2t2^t2t, leaving us with the expression below. And remember, the derivative of 2t2^t2t is whatever this whole expression on the right approaches as dt approaches 0.

At first glance this might look like an unimportant manipulation, but a tremendously important fact is that this term on the right in parentheses, where dt lives, is completely separate from the t term itself; it doesn’t depend on the actual time where we started.

What happens if you plug in a very small value for dt into a calculator to approximate this term in parentheses?

Substutiting 0.001 in for dt we get the value.

Substutiting 0.01 in for dt we get the value.

And, substutiting 0.01 in for dt we get the value.

You’l find that it for smaller and smaller choices of dt, this value approaches a specific number, around 0.693147. So the derivative of 2t2^t2t is itself multiplied by this value.

Don’t worry if that number seems mysterious, the central point is that this is some kind of constant; unlike derivatives of other functions, all the stuff that depends on dt is separate from the value of t itself.

So the derivative of 2t2^t2t is just itself, but multiplied by some constant. This should kind of make sense, because earlier it felt like the rate of growth for our population with size 2t2^t2t, at least when we were looking at changes over the course a full day. Evidently, the rate of change for this function over much smaller time scales is not quite equal to itself, but it is proportional to itself, with this very peculiar proportionality constant of 0.6931.

There’s not much special to the number 2 here. If instead had we dealt with the function 3t3^t3t, the exponential property would also have led us to the conclusion that the derivative of 3t3^t3t is proportional to itself, but this time with a proportionality constant of about 1.0986.

For any other base to your exponent you can have fun seeing what the various proportionality constants are, maybe trying to find a pattern.

Notice how the proportionality constant for the derivative changes from less than 1 for a base of 2 to greater than 1 for a base of 3.

For example, if you plug in 8 as the base of the exponential function, you can see the relevant proportionality constant is around 2.079. And maybe, just maybe, you would notice that this number happens to be exactly 3 times the constant associated with the base of 2. So these numbers aren’t random, there is some pattern. But what is the pattern? What does 2 have to do with 0.6931 and what does 8 have to do with 2.079.2

This figure illustrates different base 2 exponential functions.

A second question, which ultimately explains these mystery constants, is whether there is some base where that proportionality constant is 1; where the derivative of M(t)=atM(t) = a^tM(t)=at is not just proportional to itself, but equal to itself.

As it turns out, there is a number. It’s the special constant e, around 2.71828, called Euler''s number. In fact, it’s not just that e happens to show up here, this is, in a sense, what defines the number e 3. This special exponential function with Euler''s Number as the base is called the exponential function.

All exponential functions are proportional to their own derivative, but the exponential function base e alone is the special number so that the proportionality constant is 1, meaning ete^tet actually equals its own derivative.

If you look at the graph of ete^tet, it has the peculiar property that the slope of a tangent line to any point on the graph equals the height of that point above the horizontal axis.

Examples of the slope of the tangent line for the exponential function.

So how does the exponential function help us find the derivatives of other exponential functions? Well, maybe you noticed that different exponentials look like horizontally scaled versions of each other. This is true for all exponential functions, but best seen with exponential functions with related bases.

This means that you can re-write one exponential in terms of another''s base. For example, if we have an exponential function of base 2 and want to re-write the function in terms of base 4, it can be written like this.

One way to see how to convert between two bases is to zoom in on the graph between 0 and 1 to see how fast the first base grows to to the value of the second base. In this case, base 4 grows twice as fast as base 2 and reaches the output of 2 in half the time. So to convert base 4 to base 2 we can multiply the input t of the base 4 function by the constant 12\frac{1}{2}21​, which is the same as scaling 4x4^x4x by a factor of 2 in the horizontal direction.

So we''ve found a function, the exponential function of base e, with a really nice derivative property. Can we take any old exponential function and re-write it in terms of the exponential function? Or in other words, what constant do we multiply the input variable by to make the exponential function have the same output as another exponential function?

For example, let''s try to re-write 2t2^t2t in terms of the exponential function.

As before, we can zoom in on a plot of the two functions, and compare their behavior. Specifically, how long does it take the exponential function to grow to 2?

Well, looking at the graph, it takes about t=0.693. units which is exactly equal to the same proportionality constant we found before! If we multiply the input variable t in the exponential function by this constant, the exponential function has the same output as 2t2^t2t.

This type of question we are asking leads us directly towards another function, the inverse of the exponential function, the natural logarithm function.

The existence of a function like this can answer the question of the mystery constants, and it’s because it gives a different way to think about functions that are proportional to their own derivative. There''s nothing fancy here, this is simply the definition of the natural log, which asks the question "e to the what equals 2".

And indeed, go plug in the natural log of 2 to a calculator, and you’l find that it’s 0.6931., the mystery constant we ran into earlier. And same goes for all the other bases, the mystery proportionality constant that pops up when taking derivatives and when re-writing exponential functions using e is the natural log of the base; the answer to the question "e to the what equals that base".

Importantly, the natural logarithm function gives us the missing tool we need to find the derivative of any exponential function. The key is to re-write the function and then use the chain rule. For example, what is the derivative of the function 3t3^t3t? Well, let''s re-write this function in terms of the exponential function using the natural logarithm to calculate the horizontally-scaling proportionality constant.

Then, we can calculate the derivative of eln⁡(3)te^{\ln(3)t}eln(3)t using the chain rule by. First, take the derivative of the outermost function, which due to the special nature of the exponential funtion is itself. Then, second, multiply this by the derivative of the inner function ln⁡(3)t\ln(3)tln(3)t, which is the constant ln⁡(3)\ln(3)ln(3).

This is the same derivative we found using algebra above, since ln⁡(3)=1.09861228867.\ln(3) = 1.09861228867.ln(3)=1.09861228867.

The same technique can be used to find the derivative of any exponential function.

In fact, throughout applications of calculus, you rarely see exponentials written as some base to a power t. Instead you almost always write exponentials as e raised to some constant multiplied by t. It’s all equivalent; any function like 2t2^t2t or 3t3^t3t can be written as ec⋅te^{c \cdot t}ec⋅t. The difference is that framing things in terms of the exponential function plays much more smoothly with the process of derivatives.

I know this is all pretty symbol heavy, but the reason we care is that all sorts of natural phenomena involve a certain rate of change being proportional to the thing changing.

For example, the rate of growth of a population actually does tend to be proportional to the size of the population itself, assuming there isn’t some limited resource slowing that growth down. If you put a cup of hot water in a cool room, the rate at which the water cools is proportional to the difference in temperature between the room and the water. Or said differently, the rate at which that difference changes is proportional to itself. If you invest your money, the rate at which it grows is proportional to the amount of money there at any time.

In all these cases, where some variable’s rate of change is proportional to itself, the function describing that variable over time will be some exponential. And even though there are lots of ways to write any exponential function, it’s very natural to choose to express these functions as ecte^{ct}ect, since that constant c in the exponent carries a very natural meaning: It’s the same as the proportionality constant between the size of the changing variable and the rate of change.

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
