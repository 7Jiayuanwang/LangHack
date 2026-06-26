**文章一体化上传教程**

适用网站：Time（时代）、Newsweek（新闻周刊）、纽约时报（前七天免费）等

功能：把外网文章连同图片一键抓取，并自动上传到 LangHack 学习平台。

**前提：**

1.电脑已安装python

2.网络：下载外网文章需要开梯子；上传到学校平台需要走校园内网。（提前实现网络配置）

3.创建新文件夹，本人使用的文件夹为：

C:\Users\LENOVO\article_dl

 **一.配置** 

**1.安装依赖**

**第1步**：打开命令窗口

进入文件夹C:\Users\LENOVO\article_dl，单击上方“地址栏”，删掉里面的路径，输入powershell，回车，会弹出一个命令窗口。

**第2步**：安装需要的组件（仅需安装一次）

在命令窗口里粘贴下面这行，回车，等它跑完：

pip install playwright trafilatura beautifulsoup4 lxml requests

再粘贴下面这行，回车（下载一个浏览器内核，第一次较慢）：

python -m playwright install chromium

这两步以后都不用再做。

**2.登录外网网站**

python article_dl.py --login

会弹出浏览器，自己手动登录 NTY/Time/Newsweek.

登录态会保存在user_data/ 以后自动复用，免费文章可以跳过这一步。

 **二.只下载文章（不上传）** 

在article_dl文件夹打开powershell命令窗口，输入：

python article_dl.py "把文章网址粘贴到这里"

注意：article_dl.py 和网址之间一定要有一个空格！

![image.png](https://cdn.nlark.com/yuque/0/2026/png/68262821/1781939753113-ef4dbe99-07ca-489a-a5b9-25963cc456e8.png)



文件夹里会出现：

一个文章标题.md 文件（正文）

一个 文章标题_images 文件夹（图片，如果有的话）

 **三.获取平台登录凭证** 

上传到平台需要先把你的登录凭证（token）告诉脚本。每次 token 过期后都要重新取一次。

**第 1 步：**用浏览器正常登录 LangHack 平台。

**第 2 步****：**按 F12 打开开发者工具，点上方的 Console（控制台）。

![image.png](https://cdn.nlark.com/yuque/0/2026/png/68262821/1781940382657-81e6717c-ec78-435a-b6d0-5184a1c2e3e3.png)



**第 3 步：**在控制台里输入下面这行，回车：

localStorage.getItem('token')

**第4步：**会输出一长串eyJ开头的字符

把这串字符复制下来，不带引号，粘贴进文件夹里的 token.txt，覆盖原内容，保存。

![image.png](https://cdn.nlark.com/yuque/0/2026/png/68262821/1781940419701-604260b5-de40-4e8e-9d8f-c1f4c9b2f548.png)



![image.png](https://cdn.nlark.com/yuque/0/2026/png/68262821/1781940474793-6d0809ee-7fdb-4e37-88b0-e24e04916ae0.png)



![image.png](https://cdn.nlark.com/yuque/0/2026/png/68262821/1781940510809-0ca05108-b8e9-40fb-a602-87f0f3e636fe.png)



提示：token 通常一天左右过期。之后上传若提示登录失效，重做本节即可。

 **四. 下载并上传到平台（一体化）** 

确认 token.txt 已填好后，在 article_dl 文件夹打开 powershell，输入：

python article_dl.py --upload "文章网址"

它会自动完成全部步骤：

· 从外网抓取文章正文和图片

· 把图片上传到平台

· 把正文里的图片地址换成平台地址

· 创建一篇文章（默认标签“新闻资讯”，默认难度 MEDIUM）

![image.png](https://cdn.nlark.com/yuque/0/2026/png/68262821/1781947608958-2f74b50a-d775-4d4e-9995-22ba109d8f0e.png)



看到这样的提示就成功了：

[OK] 文章已上传到平台！textId=xxx（状态：待审核）

指定标签和难度

在网址后面再加上标签和难度，例如：

python article_dl.py --upload "文章网址" "学术英语" HARD

标签必选，并且可多选，多选时用英文逗号连起来，整体用引号包住，若写了平台没有的标签，会被自动忽略并提示，若全部不对，会回退默认成“新闻资讯”。不填则默认“新闻资讯”

难度三选一（不填则默认 MEDIUM）：EASY（简单）／ MEDIUM（中等）／ HARD（困难）

 **五. 批量上传** 

**第 1 步：**在 article_dl 文件夹新建一个文本文件，命名 urls.txt。

**第 2 步：**把多个文章网址每行一个粘贴进去，保存。

**第 3 步：**在命令窗口输入：

python article_dl.py --upload-batch urls.txt

它会一篇接一篇自动下载并上传，全部使用默认标签“新闻资讯”。

 **六.注意** 

1.token名义有效期为30天，但如果出现[未登录或登录已过期]（失效），再次去取一次token即可，覆盖。

2.免费文章可直接下载上传，若网站需要登录则先登录再抓取。