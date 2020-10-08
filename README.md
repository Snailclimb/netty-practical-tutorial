# netty-practical-tutorial

但凡涉及到网络通信就必然必然离不开网络编程。 Netty 目前作为 Java 网络编程最热门的框架，毫不夸张地说是每个 Java 程序员必备的技能之一。

**为什么说学好 Netty 很有必要呢？**

1. Netty 基于 NIO （NIO 是一种同步非阻塞的 I/O 模型，在 Java 1.4 中引入了 NIO ）。使用 Netty 可以极大地简化并简化了 TCP 和 UDP 套接字服务器等网络编程,并且性能以及安全性等很多方面都非常优秀。
2. 我们平常经常接触的 Dubbo、RocketMQ、Elasticsearch、gRPC、Spark、Elasticsearch 等等热门开源项目都用到了 Netty。
3. 大部分微服务框架底层涉及到网络通信的部分都是基于 Netty 来做的，比如说 Spring Cloud 生态系统中的网关 Spring Cloud Gateway 。

**《Netty 从入门到实战》系列文章：**

1. [从 BIO、NIO 聊到 Netty，最后还要实现个 RPC 框架！](./docs/1-fromBIO&NIOToNetty.md)
2. [手把手教你实现自己的第一个 Netty 应用](./docs/2-Netty版的HelloWorld.md)
3. [大白话 Netty 核心组件分析](./docs/3-Netty核心组件分析.md)
4. Netty TCP粘包和拆包问题分析和解决
5. Netty零拷贝
6. Netty+Kyro序列化实战
7. [手写“SpringBoot”：几十行代码使用Netty 搭建一个 HTTP Server](./example/http-server/README.md)
8. [从零开始带你写一个RPC框架！毕设/项目经验首选！](https://mp.weixin.qq.com/s/Avq4JBT-6-Dxgl7q8M251Q)
9. [Netty常见面试总结](https://xiaozhuanlan.com/topic/4028536971)

其他相关推荐

1. [Netty 指南](https://github.com/guang19/framework-learning#netty) 

