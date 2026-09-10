# ===== 本地/部署 ES（带 IK 中文分词）=====
# IK 是帖子搜索指定的分析器(ik_max_word)；官方没有 8.18 的预编译 zip，
# 用 get.infini.cloud 的国内镜像安装（GitHub 上游在部分网络不可达）。
FROM docker.elastic.co/elasticsearch/elasticsearch:8.18.1

# 【旧方案】官方 analysis-smartcn（能用但不是 IK，搜索代码用 ik_max_word 会找不到分析器）
# RUN bin/elasticsearch-plugin install --batch analysis-smartcn
# ↓ 新方案：安装 IK 分析器（与本地一致）
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/8.18.1