Kryo是一个高性能的序列化/反序列化工具，由于其变长存储特性并使用了字节码生成机制，拥有较高的运行速度和较小的字节码体积。

另外，Kryo 已经是一种非常成熟的序列化实现了，已经在Twitter、Groupon、Yahoo以及多个著名开源项目（如Hive、Storm）中广泛的使用。

 [guide-rpc-framework](https://github.com/Snailclimb/guide-rpc-framework)  就是使用的 kyro 进行序列化和反序列化。

Github 地址：[https://github.com/EsotericSoftware/kryo](https://github.com/EsotericSoftware/kryo) 。

这篇文章我会带着大家来看看 Netty 如何使用 kryo 序列化来传输对象。

### 传输实体类

**我们首先定义两个对象，这两个对象是客户端与服务端进行交互的实体类。** 客户端将 `RpcRequest` 类型的对象发送到服务端，服务端进行相应的处理之后将得到结果 `RpcResponse` 对象返回给客户端。

#### 客户端请求

`RpcRequest.java` :客户端请求实体类

```java
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Builder
@ToString
public class RpcRequest {
    private String interfaceName;
    private String methodName;
}
```

#### 服务端响应

`RpcResponse.java` ：服务端响应实体类

```java
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Builder
@ToString
public class RpcResponse {
   private String message;
}
```

### 客户端

#### 初始化客户端

**客户端中主要有一个用于向服务端发送消息的 `sendMessage()`方法，通过这个方法你可以将消息也就是`RpcRequest` 对象发送到服务端，并且你可以同步获取到服务端返回的结果也就是`RpcResponse` 对象。 **

```java
/**
 * @author shuang.kou
 * @createTime 2020年05月13日 20:48:00
 */
public class NettyClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
    private final String host;
    private final int port;
    private static final Bootstrap b;

    public NettyClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // 初始化相关资源比如 EventLoopGroup, Bootstrap
    static {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        b = new Bootstrap();
        KryoSerializer kryoSerializer = new KryoSerializer();
        b.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                // 连接的超时时间，超过这个时间还是建立不上的话则代表连接失败
                //  如果 15 秒之内没有发送数据给服务端的话，就发送一次心跳请求
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        /*
                         自定义序列化编解码器
                         */
                        // RpcResponse -> ByteBuf
                        ch.pipeline().addLast(new NettyKryoDecoder(kryoSerializer, RpcResponse.class));
                        // ByteBuf -> RpcRequest
                        ch.pipeline().addLast(new NettyKryoEncoder(kryoSerializer, RpcRequest.class));
                        ch.pipeline().addLast(new NettyClientHandler());
                    }
                });
    }

    /**
     * 发送消息到服务端
     *
     * @param rpcRequest 消息体
     * @return 服务端返回的数据
     */
    public RpcResponse sendMessage(RpcRequest rpcRequest) {
        try {
            ChannelFuture f = b.connect(host, port).sync();
            logger.info("client connect  {}", host + ":" + port);
            Channel futureChannel = f.channel();
            logger.info("send message");
            if (futureChannel != null) {
                futureChannel.writeAndFlush(rpcRequest).addListener(future -> {
                    if (future.isSuccess()) {
                        logger.info("client send message: [{}]", rpcRequest.toString());
                    } else {
                        logger.error("Send failed:", future.cause());
                    }
                });
               //阻塞等待 ，直到Channel关闭
                futureChannel.closeFuture().sync();
               // 将服务端返回的数据也就是RpcResponse对象取出
                AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse");
                return futureChannel.attr(key).get();
            }
        } catch (InterruptedException e) {
            logger.error("occur exception when connect server:", e);
        }
        return null;
    }
}
```

`sendMessage()`方法分析：

1. 首先初始化了一个 `Bootstrap`
2. 通过 `Bootstrap` 对象连接服务端
3. 通过 `Channel` 向服务端发送消息`RpcRequest`
4. 发送成功后，阻塞等待 ，直到`Channel`关闭
5. 拿到服务端返回的结果 `RpcResponse`

#### 自定义 ChannelHandler 处理服务端消息

```java
public class NettyClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            RpcResponse rpcResponse = (RpcResponse) msg;
            logger.info("client receive msg: [{}]", rpcResponse.toString());
            // 声明一个 AttributeKey 对象
            AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse");
            // 将服务端的返回结果保存到 AttributeMap 上，AttributeMap 可以看作是一个Channel的共享数据源
            // AttributeMap的key是AttributeKey，value是Attribute
            ctx.channel().attr(key).set(rpcResponse);
            ctx.channel().close();
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("client caught exception", cause);
        ctx.close();
    }
}
```

`NettyClientHandler`用于读取服务端发送过来的 `RpcResponse` 消息对象，并将 `RpcResponse` 消息对象保存到 `AttributeMap 上`，`AttributeMap` 可以看作是一个`Channel`的共享数据源。

这样的话，我们就能通过 channel 和 key 将数据读取出来。

```java
AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse");
return futureChannel.attr(key).get();
```

这个额外提一下 `AttributeMap` ,`AttributeMap` 是一个接口，但是类似于 `Map` 数据结构 。

```java
public interface AttributeMap {

    <T> Attribute<T> attr(AttributeKey<T> key);

    <T> boolean hasAttr(AttributeKey<T> key);
}
```

`Channel` 实现了 `AttributeMap` 接口，这样也就表明它存在了`AttributeMap` 相关的属性。 每个 `Channel`上的`AttributeMap`属于共享数据。`AttributeMap` 的结构，和`Map`很像，我们可以把 key 看作是`AttributeKey`，value 看作是`Attribute`，我们可以根据 `AttributeKey`找到对应的`Attribute`。

```java
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {
  ......
}
```

### 服务端

#### 初始化服务端

**`NettyServer` 主要作用就是开启了一个服务端用于接受客户端的请求并处理。**

```java
public class NettyServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private final int port;

    private NettyServer(int port) {
        this.port = port;
    }

    private void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        KryoSerializer kryoSerializer = new KryoSerializer();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // TCP默认开启了 Nagle 算法，该算法的作用是尽可能的发送大数据快，减少网络传输。TCP_NODELAY 参数的作用就是控制是否启用 Nagle 算法。
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    // 是否开启 TCP 底层心跳机制
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    //表示系统用于临时存放已完成三次握手的请求的队列的最大长度,如果连接建立频繁，服务器处理创建新连接较慢，可以适当调大这个参数
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new NettyKryoDecoder(kryoSerializer, RpcRequest.class));
                            ch.pipeline().addLast(new NettyKryoEncoder(kryoSerializer, RpcResponse.class));
                            ch.pipeline().addLast(new NettyServerHandler());
                        }
                    });

            // 绑定端口，同步等待绑定成功
            ChannelFuture f = b.bind(port).sync();
            // 等待服务端监听端口关闭
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("occur exception when start server:", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
```

#### 自定义 ChannelHandler 处理客户端消息

`NettyServerHandler` 用于介绍客户端发送过来的消息并返回结果给客户端。

```java
public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);
    private static final AtomicInteger atomicInteger = new AtomicInteger(1);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            RpcRequest rpcRequest = (RpcRequest) msg;
            logger.info("server receive msg: [{}] ,times:[{}]", rpcRequest, atomicInteger.getAndIncrement());
            RpcResponse messageFromServer = RpcResponse.builder().message("message from server").build();
            ChannelFuture f = ctx.writeAndFlush(messageFromServer);
            f.addListener(ChannelFutureListener.CLOSE);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("server catch exception",cause);
        ctx.close();
    }
}
```

### 编码器

#### 自定义编码器

**`NettyKryoEncoder` 是我们自定义的编码器。它负责处理"出站"消息，将消息格式转换为字节数组然后写入到字节数据的容器 `ByteBuf` 对象中。**

```java
/**
 * 自定义编码器。
 * <p>
 * 网络传输需要通过字节流来实现，ByteBuf 可以看作是 Netty 提供的字节数据的容器，使用它会让我们更加方便地处理字节数据。
 *
 * @author shuang.kou
 * @createTime 2020年05月25日 19:43:00
 */
@AllArgsConstructor
public class NettyKryoEncoder extends MessageToByteEncoder<Object> {
    private final Serializer serializer;
    private final Class<?> genericClass;

    /**
     * 将对象转换为字节码然后写入到 ByteBuf 对象中
     */
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Object o, ByteBuf byteBuf) {
        if (genericClass.isInstance(o)) {
            // 1. 将对象转换为byte
            byte[] body = serializer.serialize(o);
            // 2. 读取消息的长度
            int dataLength = body.length;
            // 3.写入消息对应的字节数组长度,writerIndex 加 4
            byteBuf.writeInt(dataLength);
            //4.将字节数组写入 ByteBuf 对象中
            byteBuf.writeBytes(body);
        }
    }
}

```

#### 自定义解码器

**`NettyKryoDecoder`是我们自定义的解码器。它负责处理"入站"消息，它会从 `ByteBuf` 中读取到业务对象对应的字节序列，然后再将字节序列转换为我们的业务对象。**

```java
/**
 * 自定义解码器。
 *
 * @author shuang.kou
 * @createTime 2020年05月25日 19:42:00
 */
@AllArgsConstructor
@Slf4j
public class NettyKryoDecoder extends ByteToMessageDecoder {

    private final Serializer serializer;
    private final Class<?> genericClass;

    /**
     * Netty传输的消息长度也就是对象序列化后对应的字节数组的大小，存储在 ByteBuf 头部
     */
    private static final int BODY_LENGTH = 4;

    /**
     * 解码 ByteBuf 对象
     *
     * @param ctx 解码器关联的 ChannelHandlerContext 对象
     * @param in  "入站"数据，也就是 ByteBuf 对象
     * @param out 解码之后的数据对象需要添加到 out 对象里面
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {

        //1.byteBuf中写入的消息长度所占的字节数已经是4了，所以 byteBuf 的可读字节必须大于 4，
        if (in.readableBytes() >= BODY_LENGTH) {
            //2.标记当前readIndex的位置，以便后面重置readIndex 的时候使用
            in.markReaderIndex();
            //3.读取消息的长度
            //注意： 消息长度是encode的时候我们自己写入的，参见 NettyKryoEncoder 的encode方法
            int dataLength = in.readInt();
            //4.遇到不合理的情况直接 return
            if (dataLength < 0 || in.readableBytes() < 0) {
                log.error("data length or byteBuf readableBytes is not valid");
                return;
            }
            //5.如果可读字节数小于消息长度的话，说明是不完整的消息，重置readIndex
            if (in.readableBytes() < dataLength) {
                in.resetReaderIndex();
                return;
            }
            // 6.走到这里说明没什么问题了，可以序列化了
            byte[] body = new byte[dataLength];
            in.readBytes(body);
            // 将bytes数组转换为我们需要的对象
            Object obj = serializer.deserialize(body, genericClass);
            out.add(obj);
            log.info("successful decode ByteBuf to Object");
        }
    }
}
```

#### 自定义序列化接口

`Serializer` 接口主要有两个方法一个用于序列化，一个用户反序列化。

```java
public interface Serializer {
    /**
     * 序列化
     *
     * @param obj 要序列化的对象
     * @return 字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 反序列化
     *
     * @param bytes 序列化后的字节数组
     * @param clazz 类
     * @param <T>
     * @return 反序列化的对象
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
```

#### 实现序列化接口

下面是我自定义 kryo 序列化实现类。

```java
public class KryoSerializer implements Serializer {
    /**
     * 由于 Kryo 不是线程安全的。每个线程都应该有自己的 Kryo，Input 和 Output 实例。
     * 所以，使用 ThreadLocal 存放 Kryo 对象
     */
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.register(RpcResponse.class);
        kryo.register(RpcRequest.class);
        kryo.setReferences(true);//默认值为true,是否关闭注册行为,关闭之后可能存在序列化问题，一般推荐设置为 true
        kryo.setRegistrationRequired(false);//默认值为false,是否关闭循环引用，可以提高性能，但是一般不推荐设置为 true
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Output output = new Output(byteArrayOutputStream)) {
            Kryo kryo = kryoThreadLocal.get();
            // Object->byte:将对象序列化为byte数组
            kryo.writeObject(output, obj);
            kryoThreadLocal.remove();
            return output.toBytes();
        } catch (Exception e) {
            throw new SerializeException("序列化失败");
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             Input input = new Input(byteArrayInputStream)) {
            Kryo kryo = kryoThreadLocal.get();
            // byte->Object:从byte数组中反序列化出对对象
            Object o = kryo.readObject(input, clazz);
            kryoThreadLocal.remove();
            return clazz.cast(o);
        } catch (Exception e) {
            throw new SerializeException("反序列化失败");
        }
    }

}
```

自定义序列化异常类 `SerializeException` 如下：

```java
public class SerializeException extends RuntimeException {
    public SerializeException(String message) {
        super(message);
    }
}
```

### 测试效果

**启动服务端：**

```java
new NettyServer(8889).run();
```

**启动客户端并发送 4 次消息给服务端：**

```java
RpcRequest rpcRequest = RpcRequest.builder()
  .interfaceName("interface")
  .methodName("hello").build();
NettyClient nettyClient = new NettyClient("127.0.0.1", 8889);
for (int i = 0; i < 3; i++) {
  nettyClient.sendMessage(rpcRequest);
}
RpcResponse rpcResponse = nettyClient.sendMessage(rpcRequest);
System.out.println(rpcResponse.toString());
```

**客户端控制台输出：**

![](https://guide-blog-images.oss-cn-shenzhen.aliyuncs.com/2020-8/c2883667-dae0-437c-8447-2245d27b3486.png)

**服务端控制台输出：**

![](https://guide-blog-images.oss-cn-shenzhen.aliyuncs.com/2020-8/23ab5ebf-c6e9-4d6d-8dd3-d8ee2714984c.png)

大功告成！

> 源码地址：[https://github.com/Snailclimb/guide-rpc-framework-learning/tree/master/src/main/java/github/javaguide/netty/kyro](https://github.com/Snailclimb/guide-rpc-framework-learning/tree/master/src/main/java/github/javaguide/netty/kyro) 。