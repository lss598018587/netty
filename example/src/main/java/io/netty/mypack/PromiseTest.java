package io.netty.mypack;

import io.netty.util.concurrent.*;

public class PromiseTest {
    public static void main(String[] args) {

        // 构造线程池
        EventExecutor executor = new DefaultEventExecutor();

        // 创建 DefaultPromise 实例
        Promise promise = new DefaultPromise(executor);

        // 下面给这个 promise 添加两个 listener
        promise.addListener(new GenericFutureListener<Future<Integer>>() {
            @Override
            public void operationComplete(Future future) throws Exception {
                if (future.isSuccess()) {
                    System.out.println("任务1结束，结果：" + future.get());
                } else {
                    System.out.println("任务1失败，异常：" + future.cause());
                }
            }
        }).addListener(new GenericFutureListener<Future<Integer>>() {
            @Override
            public void operationComplete(Future future) throws Exception {
                System.out.println("任务2结束，balabala...");
            }
        });

        // 提交任务到线程池，五秒后执行结束，设置执行 promise 的结果
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
                // 设置 promise 的结果
//                 promise.setFailure(new RuntimeException());
                promise.setSuccess(123456);
            }
        });

        // main 线程阻塞等待执行结果
        try {
            promise.sync();
        } catch (InterruptedException e) {
            System.out.println("异常"+e);
        }
        System.out.println("123");
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }
}
