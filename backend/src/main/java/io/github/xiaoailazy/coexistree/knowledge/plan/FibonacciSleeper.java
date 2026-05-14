package io.github.xiaoailazy.coexistree.knowledge.plan;

import org.springframework.stereotype.Component;

/**
 * 斐波那契退避睡眠（设计 §16.5）；默认 {@link Thread#sleep}，单测可 mock。
 */
@Component
public class FibonacciSleeper {

    public void sleepMillis(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
