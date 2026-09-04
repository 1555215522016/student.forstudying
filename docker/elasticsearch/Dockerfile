# ===== 本地 ES（带官方中文分词 analysis-smartcn）=====
# 用官方镜像 + 官方插件：不用自己编 IK，稳定且版本匹配 8.18
FROM docker.elastic.co/elasticsearch/elasticsearch:8.18.1

# 装官方中文分析器插件（smartcn：IK 的官方替代，简体中文按词切分）
RUN bin/elasticsearch-plugin install --batch analysis-smartcn