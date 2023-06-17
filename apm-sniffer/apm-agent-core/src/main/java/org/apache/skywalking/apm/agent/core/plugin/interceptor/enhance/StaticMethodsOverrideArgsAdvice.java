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

import java.lang.reflect.Method;

public class StaticMethodsOverrideArgsAdvice {

    private static final ILog LOGGER = LogManager.getLogger(StaticMethodsAdvice.class);

    @Advice.OnMethodEnter(inline = false, skipOn = Advice.OnDefaultValue.class, skipOnIndex = 1)
    public static Object[] enter(@Advice.Origin Class<?> clazz,
                                 @Advice.Origin Method method,
                                 @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] allArguments) throws Exception {
        return StaticMethodsAdvice.onEnter(clazz, method, allArguments);
    }

    @Advice.OnMethodExit(inline = false, onThrowable = Throwable.class)
    public static void exit(@Advice.Origin Class<?> clazz,
                            @Advice.Origin Method method,
                            @Advice.AllArguments Object[] allArguments,
                            @Advice.Thrown Throwable throwable,
                            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returnObj,
                            @Advice.Enter(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] contexts) throws Throwable {
        returnObj = StaticMethodsAdvice.onExit(clazz, method, allArguments, throwable, returnObj, (MethodInterceptResult) contexts[0]);
    }

    public static String getInterceptorClass() {
        return null;
    }
}
