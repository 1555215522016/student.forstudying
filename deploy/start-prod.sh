#!/usr/bin/env bash
# ============================================================
# 生产启动/停止脚本（放到服务器上项目根目录同级的 deploy/ 里，或在项目内运行）
# 用法：
#   ./deploy/start-prod.sh   启动（前台日志在 deploy/app.log）
#   ./deploy/start-prod.sh stop   停止
# ============================================================
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/../target/scuplus-java-0.0.1-SNAPSHOT.jar"
LOG="$DIR/app.log"
PID="$DIR/app.pid"

if [ "$1" = "stop" ]; then
  if [ -f "$PID" ] && kill -0 "$(cat "$PID")" 2>/dev/null; then
    kill "$(cat "$PID")" && echo "已停止 PID=$(cat "$PID")"
    rm -f "$PID"
  else
    echo "应用未在运行"
  fi
  exit 0
fi

if [ -f "$PID" ] && kill -0 "$(cat "$PID")" 2>/dev/null; then
  echo "应用已在运行 PID=$(cat "$PID")，日志：$LOG"
  exit 0
fi

if [ ! -f "$JAR" ]; then
  echo "找不到 $JAR"
  echo "先在本地构建：  ./mvnw.cmd package -DskipTests"
  echo "再把整个 target/ 或单个 jar 传到服务器。"
  exit 1
fi

# 显式指定 prod 档：避免误用默认的 dev（dev 会开压测旁路，是安全红线）
nohup java -Xms256m -Xmx512m -jar "$JAR" --spring.profiles.active=prod > "$LOG" 2>&1 &
echo $! > "$PID"
echo "已启动 PID=$!，日志：$LOG"
echo "查看：  tail -f $LOG"