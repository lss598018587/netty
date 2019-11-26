/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util.concurrent;

import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueueNode;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * 延迟任务管理，不仅支持延迟执行也可以根据周期一直运行。
 * @param <V>
 */
@SuppressWarnings("ComparableImplementedButEqualsNotOverridden")
final class ScheduledFutureTask<V> extends PromiseTask<V> implements ScheduledFuture<V>, PriorityQueueNode {

    //任务的创建时间，创建了当前任务则代表已经开始，因为如果是延迟任务那么就要从创建开始进行计时。
    private static final long START_TIME = System.nanoTime();

    //获取当前时间减去创建时间的时间差
    static long nanoTime() {
        return System.nanoTime() - START_TIME;
    }

    //获取最后一次的执行时间，此方法一般用于循环任务
    static long deadlineNanos(long delay) {
        //使用当前时间减去任务开始时间并且加上周期不管怎么算都会是下一次的执行时间的间隔
        //这里稍微有点绕，此处并不是使用具体的时间进行比较的而是使用时间段进行比较的，
        // 比如开始时间是00:00:00而当前时间是00:00:01他们的时间段就是1s而下一次执行周期计算应该是2s如果这样比较那么此条件不成立则不执行，
        // 直到当前时间00:00:02的时候才进行执行。而此方法就是获取下一次执行周期的计算结果。
        long deadlineNanos = nanoTime() + delay;
        // Guard against overflow
        //这里防止计算错误导致程序错误所以做了对应的处理
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    static long initialNanoTime() {
        return START_TIME;
    }

    // set once when added to priority queue
    private long id;

    //记录任务执行的周期叠加数，之前介绍了他的计算是计算的时间段而每个时间段执行都需要叠加上周期这样才能保证执行时间的准确
    //这里提一下可能有人会发现START_TIME是static描述的不管是那个对象来使用都会是一样开始时间，
    // 而为了保证执行的准确性再添加任务的时候会将已过去的周期叠加到此字段，就是调用了deadlineNanos方法，这里提到可能会有些抽象，后面使用的时候自然就会清楚。
    private long deadlineNanos;
    //周期时长，这里需要注意这个周期有三个状态
    //等于0的时候不会循环执行
    //小于0则使用scheduleWithFixedDelay方法的算法，下一次执行时间是上次执行结束的时间加周期
    //大于0则使用scheduleAtFixedRate方法的算法，下一次执行时间是上一次执行时间加周期
    //大于小于的两者差距在前面详细的介绍过遗忘的读者可以再去阅读。
    private final long periodNanos;
    //刚才讲述了PriorityQueueNode有说存储当前node是在队列中的那个下标，而此变量则是对列存储的下标
    private int queueIndex = INDEX_NOT_IN_QUEUE;

    //构造器，传入执行器、运行的Runnable，因为是Runnable所以传入了result，执行的时间。
    //可以看出此方法是延迟执行任务的构造，因为没有传入周期，执行一次即可结束。
    //此处的执行时间是执行开始时间，而这个时间的算法就是deadlineNanos方法的调用
    ScheduledFutureTask(AbstractScheduledEventExecutor executor,
            Runnable runnable, long nanoTime) {

        super(executor, runnable);
        deadlineNanos = nanoTime;
        periodNanos = 0;
    }

    ScheduledFutureTask(AbstractScheduledEventExecutor executor,
            Runnable runnable, long nanoTime, long period) {

        super(executor, runnable);
        deadlineNanos = nanoTime;
        periodNanos = validatePeriod(period);
    }


    ScheduledFutureTask(AbstractScheduledEventExecutor executor,
            Callable<V> callable, long nanoTime, long period) {

        super(executor, callable);
        deadlineNanos = nanoTime;
        periodNanos = validatePeriod(period);
    }
    //此处是对第一个构造的一个调用实现，因为第一个构造传入的是Runnable而ScheduledFutureTask使用的任务是Callable所以第一个构造调用了一个转换的方法然后调用此构造
    //可以看出默认周期是0则代表此构造是不重复运行的
    ScheduledFutureTask(AbstractScheduledEventExecutor executor,
            Callable<V> callable, long nanoTime) {

        super(executor, callable);
        deadlineNanos = nanoTime;
        periodNanos = 0;
    }

    private static long validatePeriod(long period) {
        if (period == 0) {
            throw new IllegalArgumentException("period: 0 (expected: != 0)");
        }
        return period;
    }

    ScheduledFutureTask<V> setId(long id) {
        if (this.id == 0L) {
            this.id = id;
        }
        return this;
    }

    //获取执行器
    @Override
    protected EventExecutor executor() {
        return super.executor();
    }

    //获取执行时间
    public long deadlineNanos() {
        return deadlineNanos;
    }

    void setConsumed() {
        // Optimization to avoid checking system clock again
        // after deadline has passed and task has been dequeued
        if (periodNanos == 0) {
            assert nanoTime() > deadlineNanos;
            deadlineNanos = 0L;
        }
    }

    public long delayNanos() {
        return deadlineToDelayNanos(deadlineNanos());
    }

    static long deadlineToDelayNanos(long deadlineNanos) {
        return deadlineNanos == 0L ? 0L : Math.max(0L, deadlineNanos - nanoTime());
    }

    //上一个方法使用的是当前时间而此方法使用的是传入的指定时间
    public long delayNanos(long currentTimeNanos) {
        return deadlineNanos == 0L ? 0L
                : Math.max(0L, deadlineNanos() - (currentTimeNanos - START_TIME));
    }

    //将获取到的时长转为指定的时间类型，获取到的试纳秒如果传入的unit是秒或者毫秒则会转成对象的时长返回
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
    }

    //之前再说Delayed接口的时候，此结构基础过Comparable接口所以整个方法实现的Comparable接口的方法
    //此方法是比较两个ScheduledFutureTask的周期任务是下次执行的时长，因为既然是在队列中那么每次弹出的任务都会是头部的，所以是为了将先执行的任务排到队列头使用。
    //此函数具体的返回值需要根据使用出做出判定此处不做解释
    @Override
    public int compareTo(Delayed o) {
        if (this == o) {
            return 0;
        }

        ScheduledFutureTask<?> that = (ScheduledFutureTask<?>) o;
        //当前的执行时间减去传入的执行时间，获取的就是他们的差数
        long d = deadlineNanos() - that.deadlineNanos();
        //如果小于0 则代表当前的时间执行早于传入的时间则返回-1

        if (d < 0) {
            return -1;
        }
        //如果大于0则代表当前任务晚于传入的时间则返回1
        else if (d > 0) {
            return 1;
        }
        //如果他俩下一个周期时间相等则代表d是0，则判断他当前的id是否小于传入的id，如果小则代表当前任务优先于传入的任务则返回-1
        else if (id < that.id) {
            return -1;
        } else {
            //如果两个id相等则抛出异常
            assert id != that.id;
            //否则传入的任务优先于当前的任务，此处结论是根据调用出总结。
            return 1;
        }
    }

    @Override
    public void run() {
        //如果当前线程不是传入的执行器线程则会抛出断言异常当然如果运行时没有开启断言关键字那么次代码无效
        assert executor().inEventLoop();
        try {

            if (delayNanos() > 0L) {
                // Not yet expired, need to add or remove from queue
                if (isCancelled()) {
                    scheduledExecutor().scheduledTaskQueue().removeTyped(this);
                } else {
                    scheduledExecutor().scheduleFromEventLoop(this);
                }
                return;
            }
            //检查是否周期为0之前说过如果是0则不进行循环
            if (periodNanos == 0) {
                //与父级的使用相同设置为状态为正在运运行
                if (setUncancellableInternal()) {
                    //执行任务
                    V result = runTask();
                    //设置为成功
                    setSuccessInternal(result);
                }
            } else {
                //检查当前的任务是否被取消了
                if (!isCancelled()) {
                    //如果没有则调用call，因为能进入这里都是循环执行的任务所以没有返回值
                    runTask();
                    //并且判断当前的执行器是否已经关闭
                    if (!executor().isShutdown()) {
                        //如果当前周期大于0则代表当前时间添加周期时间
                        //这里需要注意当前时间包括了不包括执行时间
                        //这样说可能有点绕，这样理解这里的p是本次执行是在开始的准时间，什么是准时间？就是无视任务的执行时间以周期时间和执行开始时间计算。
                        //scheduleAtFixedRate方法的算法，通过下面的deadlineNanos+=p也是可以看出的。
                        if (periodNanos > 0) {
                            deadlineNanos += periodNanos;
                        } else {
                            //此处小于0 则就需要将当前程序的运行时间也要算进去所以使用了当前时间加周期，p因为小于0所以负负得正了
                            deadlineNanos = nanoTime() - periodNanos;
                        }
                        //如果还没有取消当前任务
                        if (!isCancelled()) {
                            //获取任务队列并且将当前的任务在丢进去，因为已经计算完下一次执行的时间了所以当前任务已经是一个新的任务，最起码执行时间改变了
                            scheduledExecutor().scheduledTaskQueue().add(this);
                        }
                    }
                }
            }
        } catch (Throwable cause) {
            setFailureInternal(cause);
        }
    }

    private AbstractScheduledEventExecutor scheduledExecutor() {
        return (AbstractScheduledEventExecutor) executor();
    }

    //取消当前任务所以需要从任务队列中移除当前任务
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean canceled = super.cancel(mayInterruptIfRunning);
        if (canceled) {
            scheduledExecutor().removeScheduled(this);
        }
        return canceled;
    }

    //取消不删除则直接调用父级方法不做任务的删除，
    boolean cancelWithoutRemove(boolean mayInterruptIfRunning) {
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    protected StringBuilder toStringBuilder() {
        StringBuilder buf = super.toStringBuilder();
        buf.setCharAt(buf.length() - 1, ',');

        return buf.append(" deadline: ")
                  .append(deadlineNanos)
                  .append(", period: ")
                  .append(periodNanos)
                  .append(')');
    }

    //获取在队列中的位置
    @Override
    public int priorityQueueIndex(DefaultPriorityQueue<?> queue) {
        return queueIndex;
    }

    //设置当前任务在队列中的位置
    @Override
    public void priorityQueueIndex(DefaultPriorityQueue<?> queue, int i) {
        queueIndex = i;
    }
}
