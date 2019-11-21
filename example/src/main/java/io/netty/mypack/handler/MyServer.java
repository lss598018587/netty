package io.netty.mypack.handler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class MyServer {

    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup();

        try{
            ServerBootstrap serverBootstrap =new ServerBootstrap();
            serverBootstrap.group(bossGroup,workGroup).channel(NioServerSocketChannel.class).childHandler(
                    new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline channelPipeline =  ch.pipeline();
                            System.out.println(channelPipeline.names());
                            channelPipeline.addLast("t1",new MyByteToLongDecoder2());
                            channelPipeline.addLast("t2",new MyLongToByteEncoder());
                            channelPipeline.addLast("t3",new MyServerHander());
                            System.out.println(channelPipeline.names());
                        }
                    }
            );

            ChannelFuture f = serverBootstrap.bind(8889).sync();
            f.channel().closeFuture().sync();


        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {

        }
    }
}
