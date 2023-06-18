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

package org.apache.skywalking.apm.agent.bytebuddy.case1.advice1;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ExtForAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.skywalking.apm.agent.bytebuddy.Log;
import org.apache.skywalking.apm.agent.bytebuddy.SWClassFileLocator;
import org.apache.skywalking.apm.agent.bytebuddy.case1.AbstractInterceptTest;
import org.apache.skywalking.apm.agent.bytebuddy.util.BytecodeUtils;
import org.junit.Assert;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class AdaptedAdviceClassTest1 extends AbstractInterceptTest {

    public static void main(String[] args) throws Exception {

        enableClassDump();

        Instrumentation instrumentation = ByteBuddyAgent.install();

        String interceptorClass1 = "MyClassInterceptor1";
        Class<?> adviceClass1 = BytecodeUtils.replaceConstant(MyAdvice.class, MyAdvice.getInterceptorClass(),
                interceptorClass1, MyAdvice.class.getName() + "$" + interceptorClass1);

        String interceptorClass2 = "MyClassInterceptor2";
        Class<?> adviceClass2 = BytecodeUtils.replaceConstant(MyAdvice.class, MyAdvice.getInterceptorClass(),
                interceptorClass2, MyAdvice.class.getName() + "$" + interceptorClass2);

        SWClassFileLocator classFileLocator = new SWClassFileLocator(instrumentation, adviceClass1.getClassLoader(), new String[]{"$"});

        // advice
        AgentBuilder.Transformer transformer = new ExtForAdvice(classFileLocator)
                .advice(named("sayHello"), adviceClass1.getName())
                .advice(named("sayHello"), adviceClass2.getName());

        new AgentBuilder.Default()
                .type(ElementMatchers.nameEndsWith("MyClass"))
                .transform(transformer)
                .with(classFileLocator)
                .with(createListener())
                .installOn(instrumentation);

        // interceptor
        // installInterceptor(instrumentation, adviceClass1, adviceClass2, classFileLocator);

        //instrumentation.retransformClasses(MyClass.class);

        MyClass instance = new MyClass();
        String str = instance.sayHello("Tom");
        Log.info("result: " + str);

        Log.info("");
        str = instance.sayHello("Joe");
        Log.info("result: " + str);
        Assert.assertEquals("override arg failed.", str, "Hi, Joe boy boy");

        try {
            Log.info("");
            str = instance.sayHello("Cat");
            Log.info("result: " + str);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        TimeUnit.HOURS.sleep(1);
    }

    private static void installInterceptor(Instrumentation instrumentation, Class<?> adviceClass1, Class<?> adviceClass2, SWClassFileLocator classFileLocator) {
        //newAgentBuilder("sw")
        new AgentBuilder.Default()
                .type(ElementMatchers.nameEndsWith("MyClass"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        {
                            try {
                                return builder.method(named("sayHello"))
                                        .intercept(Advice.to(adviceClass1, classFileLocator));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        {
                            try {
                                return builder.method(named("sayHello"))
                                        .intercept(Advice.to(adviceClass2, classFileLocator));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
                .with(createListener())
                .installOn(instrumentation);
    }

    private static AgentBuilder.Listener createListener() {
        return new AgentBuilder.Listener() {
            @Override
            public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
            }

            @Override
            public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
            }

            @Override
            public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {
            }

            @Override
            public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
                System.err.println(String.format("transform class error: typeName: %s, ", typeName));
                throwable.printStackTrace();
            }

            @Override
            public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {

            }
        };
    }

}

class MyClass {
    public String sayHello(String message) {
        Log.info("Execute origin method, arg: " + message);
        if (message.contains("Cat")) {
            throw new IllegalArgumentException("Invalid");
        }
        return "Hi, " + message;
    }
}

class MyAdvice {

    private static final String INTERCEPTOR_CLASS = "INTERCEPTOR_CLASS";

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, skipOnIndex = 1)
    public static Object[] enter(@Advice.Origin Method method,
                                 @Advice.This Object target,
                                 @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] allArguments) {
        Object[] argsRef = allArguments;
        Object[] objects = MyAdviceSupport.enter(method, target, argsRef, INTERCEPTOR_CLASS);
        allArguments = argsRef;
        return objects;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Origin Method method,
                            @Advice.This Object target,
                            @Advice.AllArguments Object[] allArguments,
                            @Advice.Thrown Throwable throwable,
                            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returnObj,
                            @Advice.Enter(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] contexts) {
        // inline: change returnObj value
        returnObj = MyAdviceSupport.exit(method, target, allArguments, throwable, returnObj, contexts);
    }

    public static String getInterceptorClass() {
        return INTERCEPTOR_CLASS;
    }

}

class MyAdviceSupport {
    public static Object[] enter(Method method, Object target, Object[] allArguments, String interceptorClass) {
        String message = (String) allArguments[0];
        Log.info(String.format("Before method, arg: %s, interceptorClass: %s", message, interceptorClass));

        allArguments[0] = message + " boy";
        MyContext context = new MyContext();
        context.setInterceptorClass(interceptorClass);

        // Do something special ..
        if (message.contains("Tom")) {
            context.setSkip(true);
            context.setValue("Reject");
        }

        if (context.isSkip()) {
            return new Object[]{context, null};
        } else {
            return new Object[]{context, true};
        }
    }

    public static Object exit(Method method, Object target, Object[] allArguments, Throwable throwable, Object returnObj,
                              Object[] contexts) {
        MyContext context = (MyContext) contexts[0];
        if (throwable != null) {
            Log.info("Thrown on method: " + throwable);
        } else {
            if (context.isSkip()) {
                returnObj = context.value;
                Log.info("Skip origin method and set return value: " + returnObj);
            } else {
                Log.info("Exit method with return value: " + returnObj);
            }
        }
        return returnObj;
    }
}

class MyContext {
    boolean skip;
    Object value;
    String interceptorClass;

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public String getInterceptorClass() {
        return interceptorClass;
    }

    public void setInterceptorClass(String interceptorClass) {
        this.interceptorClass = interceptorClass;
    }
}
