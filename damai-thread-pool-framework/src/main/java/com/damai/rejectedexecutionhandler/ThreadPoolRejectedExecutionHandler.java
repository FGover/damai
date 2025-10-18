package com.damai.rejectedexecutionhandler;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 拒绝策略
 * @author: 阿星不是程序员
 **/
public class ThreadPoolRejectedExecutionHandler {

    /**
     * 业务中断型拒绝策略，当任务被拒绝时直接抛出异常
     * 适用于对任务提交成功率有严格要求的场景，需要上层调用者处理异常情况
     */
    public static class BusinessAbortPolicy implements RejectedExecutionHandler {

        public BusinessAbortPolicy() {
        }

        /**
         * 任务被拒绝时的处理逻辑
         * @param r the runnable task requested to be executed
         * @param executor the executor attempting to execute this task
         */
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

            throw new RejectedExecutionException("threadPoolApplicationName business task " + r.toString() +
                    " rejected from " +
                    executor.toString());
        }
    }
}
