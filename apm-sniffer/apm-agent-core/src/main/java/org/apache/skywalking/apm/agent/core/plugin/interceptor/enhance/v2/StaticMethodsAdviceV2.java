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

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;

import java.lang.reflect.Method;

public class StaticMethodsAdviceV2 {

    public static final String INTERCEPTOR_CLASS = "INTERCEPTOR_CLASS";

    private static final ILog LOGGER = LogManager.getLogger(StaticMethodsAdviceV2.class);

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, skipOnIndex = 1)
    public static Object[] enter(@Advice.Origin Class<?> clazz,
                                 @Advice.Origin Method method,
                                 @Advice.AllArguments Object[] allArguments) throws Exception {
        return onEnter(clazz, method, allArguments, INTERCEPTOR_CLASS);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Origin Class<?> clazz,
                            @Advice.Origin Method method,
                            @Advice.AllArguments Object[] allArguments,
                            @Advice.Thrown Throwable throwable,
                            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returnObj,
                            @Advice.Enter(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] contexts) throws Throwable {

        // inline change return value
        returnObj = onExit(clazz, method, allArguments, throwable, returnObj, (MethodInvocationContext) contexts[0], INTERCEPTOR_CLASS);
    }

    public static Object[] onEnter(Class<?> clazz, Method method, Object[] allArguments, String interceptorClass) throws Exception {
        StaticMethodsAroundInterceptorV2 interceptor = InterceptorInstanceLoader.load(interceptorClass, clazz
                .getClassLoader());
        MethodInvocationContext context = new MethodInvocationContext();

        try {
            interceptor.beforeMethod(clazz, method, allArguments, method.getParameterTypes(), context);
        } catch (Throwable t) {
            LOGGER.error(t, "class[{}] before static method[{}] intercept failure", clazz, method.getName());
        }

        if (!context.isContinue()) {
            return new Object[]{context, null};
        } else {
            return new Object[]{context, true};
        }
    }

    public static Object onExit(Class<?> clazz, Method method, Object[] allArguments, Throwable throwable, Object returnObj,
                                MethodInvocationContext context, String interceptorClass) throws Throwable {
        if (!context.isContinue()) {
            returnObj = context._ret();
        }
        StaticMethodsAroundInterceptorV2 interceptor = InterceptorInstanceLoader.load(interceptorClass, clazz
                .getClassLoader());

        if (throwable != null) {
            try {
                interceptor.handleMethodException(clazz, method, allArguments, method.getParameterTypes(), throwable, context);
            } catch (Throwable t2) {
                LOGGER.error(t2, "class[{}] handle static method[{}] exception failure", clazz, method.getName(), t2.getMessage());
            }
        }

        try {
            returnObj = interceptor.afterMethod(clazz, method, allArguments, method.getParameterTypes(), returnObj, context);
        } catch (Throwable t) {
            LOGGER.error(t, "class[{}] after static method[{}] intercept failure:{}", clazz, method.getName(), t.getMessage());
        }

        return returnObj;
    }
}
