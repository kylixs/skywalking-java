/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;

import java.lang.reflect.Method;

public class InstanceMethodsAdvice {

    public static final String INTERCEPTOR_CLASS = "INTERCEPTOR_CLASS";

    private static final ILog LOGGER = LogManager.getLogger(StaticMethodsAdvice.class);

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, skipOnIndex = 1)
    public static Object[] enter(@Advice.This Object objInst,
                                 @Advice.Origin Method method,
                                 @Advice.AllArguments Object[] allArguments) throws Exception {
        return onEnter((EnhancedInstance) objInst, method, allArguments, INTERCEPTOR_CLASS);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.This Object objInst,
                            @Advice.Origin Method method,
                            @Advice.AllArguments Object[] allArguments,
                            @Advice.Thrown Throwable throwable,
                            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returnObj,
                            @Advice.Enter(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] contexts) throws Exception {

        // inline change return value
        returnObj = onExit((EnhancedInstance) objInst, method, allArguments, throwable, returnObj, (MethodInterceptResult) contexts[0], INTERCEPTOR_CLASS);
    }

    public static Object[] onEnter(EnhancedInstance objInst, Method method, Object[] allArguments, String interceptorClass) throws Exception {
        Class<?> clazz = objInst.getClass();
        InstanceMethodsAroundInterceptor interceptor = InterceptorInstanceLoader.load(interceptorClass, clazz
                .getClassLoader());
        MethodInterceptResult result = new MethodInterceptResult();

        try {
            interceptor.beforeMethod(objInst, method, allArguments, method.getParameterTypes(), result);
        } catch (Throwable t) {
            LOGGER.error(t, "class[{}] before static method[{}] intercept failure", clazz, method.getName());
        }

        if (!result.isContinue()) {
            return new Object[]{result, null};
        } else {
            return new Object[]{result, true};
        }
    }

    public static Object onExit(EnhancedInstance objInst, Method method, Object[] allArguments, Throwable throwable, Object returnObj,
                                MethodInterceptResult interceptResult, String interceptorClass) throws Exception {
        Class<?> clazz = objInst.getClass();
        InstanceMethodsAroundInterceptor interceptor = InterceptorInstanceLoader.load(interceptorClass, clazz
                .getClassLoader());

        if (!interceptResult.isContinue()) {
            returnObj = interceptResult._ret();
        }

        if (throwable != null) {
            try {
                interceptor.handleMethodException(objInst, method, allArguments, method.getParameterTypes(), throwable);
            } catch (Throwable t2) {
                LOGGER.error(t2, "class[{}] handle static method[{}] exception failure", clazz, method.getName(), t2.getMessage());
            }
        }

        try {
            returnObj = interceptor.afterMethod(objInst, method, allArguments, method.getParameterTypes(), returnObj);
        } catch (Throwable t) {
            LOGGER.error(t, "class[{}] after static method[{}] intercept failure:{}", clazz, method.getName(), t.getMessage());
        }

        return returnObj;
    }
}
