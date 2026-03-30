package io.github.xiaoailazy.coexistree.knowledge.tree;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 系统知识树节点 ID 生成器
 * <p>
 * 生成格式：{systemCode}_{序号}
 * 序号全局递增，永不回收
 */
public class SystemTreeNodeIdGenerator {
    
    private final String systemCode;
    private final AtomicInteger counter;
    
    /**
     * 构造函数
     * 
     * @param systemCode 系统代码
     */
    public SystemTreeNodeIdGenerator(String systemCode) {
        if (systemCode == null || systemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("systemCode cannot be null or empty");
        }
        this.systemCode = systemCode;
        this.counter = new AtomicInteger(1);
    }
    
    /**
     * 生成下一个节点 ID
     * 
     * @return 节点 ID，格式：{systemCode}_{序号}
     */
    public String nextId() {
        int nextValue = counter.getAndIncrement();
        return systemCode + "_" + nextValue;
    }
    
    /**
     * 重置计数器为初始值（1）
     */
    public void reset() {
        counter.set(1);
    }
    
    /**
     * 设置计数器为指定值（用于从现有树恢复）
     * 
     * @param counterValue 计数器值
     */
    public void setCounter(int counterValue) {
        if (counterValue < 1) {
            throw new IllegalArgumentException("counter must be >= 1");
        }
        counter.set(counterValue);
    }
    
    /**
     * 获取当前计数器值（用于测试）
     * 
     * @return 当前计数器值
     */
    int getCurrentCounter() {
        return counter.get();
    }
}
