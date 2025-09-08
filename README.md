# AsTimeWheel - 高性能时间轮定时任务调度引擎

## 项目简介

AsTimeWheel 是一个基于哈希时间轮算法实现的高性能定时任务调度中间件，适用于需要管理大量短周期定时任务的场景。项目灵感来源于Netty的HashedWheelTimer，并在此基础上进行了优化和扩展。

## 核心特性

- **高效调度**：O(1)时间复杂度添加/取消任务
- **低资源消耗**：单线程处理所有任务，减少上下文切换
- **精确控制**：支持纳秒级时间精度
- **灵活配置**：可调的时间轮参数适应不同场景
- **简单API**：易用的任务调度接口
- **生产就绪**：完善的异常处理和资源管理

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.asthenia0412</groupId>
    <artifactId>astimewheel-core</artifactId>
    <version>最新版本</version>
</dependency>
```

### 2. 基本使用

```java
// 创建时间轮定时器
HashedWheelTimer timer = new HashedWheelTimer(
    100, TimeUnit.MILLISECONDS, 512);

// 创建调度器
TimeWheelScheduler scheduler = new DefaultTimeWheelScheduler(timer);

// 调度延迟任务
String taskId = scheduler.schedule(() -> {
    System.out.println("任务执行");
}, 1, TimeUnit.SECONDS);

// 调度周期性任务
String periodicTaskId = scheduler.scheduleAtFixedRate(() -> {
    System.out.println("周期性任务");
}, 0, 5, TimeUnit.SECONDS);

// 取消任务
scheduler.cancel(periodicTaskId);

// 关闭调度器
scheduler.shutdown();
```

### 3. Spring Boot集成

添加starter依赖：

```xml
<dependency>
    <groupId>io.github.asthenia0412</groupId>
    <artifactId>astimewheel-spring-boot-starter</artifactId>
    <version>最新版本</version>
</dependency>
```

配置参数（application.yml）：

```yaml
timewheel:
  tick-duration: 100       # 每个tick的持续时间
  time-unit: MILLISECONDS  # 时间单位
  ticks-per-wheel: 512     # 时间轮槽数
```

注入使用：

```java
@Autowired
private TimeWheelScheduler scheduler;
```

## 配置参数

| 参数 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| tick-duration | long | 100 | 每个tick的持续时间 |
| time-unit | TimeUnit | MILLISECONDS | 时间单位 |
| ticks-per-wheel | int | 512 | 时间轮槽数 |

## 性能建议

1. **高精度场景**（延迟敏感）：
   - tick-duration: 10-50ms
   - ticks-per-wheel: 256-512

2. **高吞吐场景**（批量任务）：
   - tick-duration: 100-500ms
   - ticks-per-wheel: 512-1024

3. **长延迟场景**：
   - tick-duration: 1-5s
   - ticks-per-wheel: 1024-2048

## 最佳实践

1. 任务处理逻辑应尽量简短，避免阻塞工作线程
2. 对于耗时任务，建议提交到独立线程池执行
3. 合理设置tick-duration平衡精度和性能
4. 使用shutdown()确保资源正确释放
5. 监控任务执行时间和取消率

## 开发指南

### 构建项目

```bash
mvn clean install
```

### 运行测试

```bash
mvn test
```

## 贡献

欢迎提交Issue和Pull Request。请确保：

1. 代码符合现有风格
2. 包含必要的测试用例
3. 更新相关文档

## 许可证

Apache License 2.0

## 致谢

本项目参考了Netty的HashedWheelTimer实现，并受到Kafka和ZooKeeper等项目中时间轮应用的启发。