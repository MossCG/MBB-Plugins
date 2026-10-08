# MBB-PigHub

MoBoxBot 猪猪图片插件。

发送关键词：

```text
来只猪猪
```

机器人会从 PigHub 随机选择一张猪猪图片发送。

## 配置

```yaml
keyword: "来只猪猪"
apiUrl: "https://pighub.top/api/images?sort=2"
cacheMinutes: 10
replyText: "猪猪来啦！"
```

PigHub 没有单独的随机接口，前端随机模式是拉取图片列表后洗牌；插件按同样方式获取列表并本地随机。
