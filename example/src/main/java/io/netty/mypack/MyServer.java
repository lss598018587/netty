package io.netty.mypack;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.mypack.handler.MyByteToLongDecoder2;
import io.netty.mypack.handler.MyLongToByteEncoder;
import io.netty.mypack.handler.MyServerHander;

public class MyServer {

    public static void main(String[] args) {


        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(4);
        try{
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup,workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new MyWmHandler())
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast("t1",new MyByteToLongDecoder2());
                            p.addLast("t2",new MyLongToByteEncoder());
                            p.addLast("t3",new MyServerHander());
                        }
                    });
            //sync 等待绑定和初始化注册全部成功 才返回 ChannelFuture 对象
            ChannelFuture f= b.bind(8000).sync();


            //sync 等待channel 真正关闭了，流程才能往下走
            f.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //即会关闭连接，也会释放资源
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
//        ConcurrentHashMap<String,String> s = new ConcurrentHashMap<>();
//        System.out.println(s.put("1","2"));
//        System.out.println(ChannelOption.valueOf("CONNECT_TIMEOUT_MILLIS"));
    }

}
