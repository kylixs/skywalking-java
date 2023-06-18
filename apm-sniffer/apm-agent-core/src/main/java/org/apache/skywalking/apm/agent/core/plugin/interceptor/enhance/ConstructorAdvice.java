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
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;

public class ConstructorAdvice {

    public static final String INTERCEPTOR_CLASS = "INTERCEPTOR_CLASS";

    private static final ILog LOGGER = LogManager.getLogger(StaticMethodsAdvice.class);

    @Advice.OnMethodExit
    public static void exit(@Advice.This Object objInst,
                            @Advice.AllArguments Object[] allArguments) throws Exception {

        onConstruct((EnhancedInstance) objInst, allArguments, INTERCEPTOR_CLASS);
    }

    public static void onConstruct(EnhancedInstance objInst, Object[] allArguments, String interceptorClass) throws Exception {
        Class<?> clazz = objInst.getClass();
        InstanceConstructorInterceptor interceptor = InterceptorInstanceLoader.load(interceptorClass, clazz
                .getClassLoader());

        try {
            interceptor.onConstruct(objInst, allArguments);
        } catch (Throwable t) {
            LOGGER.error("ConstructorInter failure.", t);
        }
    }
}
