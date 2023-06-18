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

package org.apache.skywalking.apm.agent.bytebuddy.case1.advice2;

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
import java.util.Arrays;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Intercept target class method twice with two different interceptor classes
 */
public class AdaptedAdviceClassTest2 extends AbstractInterceptTest {

    public static void main(String[] args) throws Exception {

        enableClassDump();

        Instrumentation instrumentation = ByteBuddyAgent.install();

        String interceptorClass1 = MyClassInterceptor1.class.getName();
        Class<?> adviceClass1 = BytecodeUtils.replaceConstant(InstanceMethodsAdvice.class, InstanceMethodsAdvice.getInterceptorClass(),
                interceptorClass1, InstanceMethodsAdvice.class.getName() + "$" + interceptorClass1);

        String interceptorClass2 = MyClassInterceptor2.class.getName();
        Class<?> adviceClass2 = BytecodeUtils.replaceConstant(InstanceMethodsAdvice.class, InstanceMethodsAdvice.getInterceptorClass(),
                interceptorClass2, InstanceMethodsAdvice.class.getName() + "$" + interceptorClass2);

        String constructorInterceptorClass1 = "constructorInterceptorClass";
        Class<?> constructorAdviceClass1 = BytecodeUtils.replaceConstant(InstanceConstructorAdvice.class, InstanceConstructorAdvice.getInterceptorClass(),
                constructorInterceptorClass1, InstanceMethodsAdvice.class.getName() + "$" + constructorInterceptorClass1);

        SWClassFileLocator classFileLocator = new SWClassFileLocator(instrumentation, adviceClass1.getClassLoader(), new String[]{"$"});

        // advice
        AgentBuilder.Transformer transformer = new ExtForAdvice(classFileLocator)
                .advice(named("sayHello"), adviceClass1.getName())
                .advice(named("sayHello"), adviceClass2.getName())
                .advice(ElementMatchers.isConstructor(), constructorAdviceClass1.getName());

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
        Assert.assertEquals("skip origin method call failed.", "Reject: Tom", str);

        Log.info("");
        str = instance.sayHello("User");
        Log.info("result: " + str);
        Assert.assertEquals("override arg failed.", "Reject: User boy", str);

        Log.info("");
        str = instance.sayHello("Joe");
        Log.info("result: " + str);
        Assert.assertEquals("override arg failed.", "Hi, Joe boy girl", str);

        try {
            Log.info("");
            str = instance.sayHello("Cat");
            Log.info("result: " + str);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //TimeUnit.HOURS.sleep(1);
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

class MyClassInterceptor1 implements InstanceMethodsInterceptor {

    @Override
    public void beforeMethod(Object objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Log.info(String.format("MyClassInterceptor1.beforeMethod: %s, allArguments: %s", method.getName(), Arrays.asList(allArguments)));
        String arg0 = (String) allArguments[0];
        if (arg0.contains("Tom")) {
            result.defineReturnValue("Reject: " + arg0);
            return;
        }
        allArguments[0] += " boy";
        Log.info(String.format("Change args: %s", allArguments));
    }

    @Override
    public Object afterMethod(Object objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Log.info(String.format("MyClassInterceptor1.afterMethod: %s, allArguments: %s, ret: %s", method.getName(), Arrays.asList(allArguments), ret));
        return ret;
    }

    @Override
    public void handleMethodException(Object objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        Log.info(String.format("MyClassInterceptor1.handleMethodException: $s, allArguments: %s, error: %s", method.getName(), Arrays.asList(allArguments), t));
    }
}

class MyClassInterceptor2 implements InstanceMethodsInterceptor {

    @Override
    public void beforeMethod(Object objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Log.info(String.format("MyClassInterceptor2.beforeMethod: %s, allArguments: %s", method.getName(), Arrays.asList(allArguments)));
        String arg0 = (String) allArguments[0];
        if (arg0.contains("User")) {
            result.defineReturnValue("Reject: " + arg0);
            return;
        }
        allArguments[0] += " girl";
        Log.info(String.format("Change args: %s", allArguments));
    }

    @Override
    public Object afterMethod(Object objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Log.info(String.format("MyClassInterceptor2.afterMethod: %s, allArguments: %s, ret: %s", method.getName(), Arrays.asList(allArguments), ret));
        return ret;
    }

    @Override
    public void handleMethodException(Object objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        Log.info(String.format("MyClassInterceptor2.handleMethodException: $s, allArguments: %s, error: %s", method.getName(), Arrays.asList(allArguments), t));
    }
}

class InstanceMethodsAdvice {

    private static final String INTERCEPTOR_CLASS = "INTERCEPTOR_CLASS";

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, skipOnIndex = 0)
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

class InstanceConstructorAdvice {

    private static final String INTERCEPTOR_CLASS = "INTERCEPTOR_CLASS";

    @Advice.OnMethodExit
    public static void constructor(@Advice.This Object target,
                                   @Advice.AllArguments Object[] allArguments) {
        MyAdviceSupport.onConstructor(target, allArguments, INTERCEPTOR_CLASS);
    }

    public static String getInterceptorClass() {
        return INTERCEPTOR_CLASS;
    }

}

class MyAdviceSupport {
    public static Object[] enter(Method method, Object target, Object[] allArguments, String interceptorClass) {
        InstanceMethodsInterceptor interceptor = null;
        MethodInterceptResult context = new MethodInterceptResult();

        try {
            interceptor = (InstanceMethodsInterceptor) Class.forName(interceptorClass).newInstance();

            interceptor.beforeMethod(target, method, allArguments, null, context);

        } catch (Throwable e) {
            System.err.println(String.format("call beforeMethod failed: %s, interceptor: %s", e, interceptor));
        }

        if (!context.isContinue()) {
            return new Object[]{null, context, interceptor};
        } else {
            return new Object[]{true, context, interceptor};
        }
    }

    public static Object exit(Method method, Object target, Object[] allArguments, Throwable throwable, Object returnObj,
                              Object[] contexts) {
        MethodInterceptResult context = (MethodInterceptResult) contexts[1];
        InstanceMethodsInterceptor interceptor = (InstanceMethodsInterceptor) contexts[2];
        if (throwable != null) {
            interceptor.handleMethodException(target, method, allArguments, null, throwable);
        } else {
            if (!context.isContinue()) {
                returnObj = context._ret();
                Log.info(String.format("Skip origin method and set return value: %s, interceptor: %s", returnObj, interceptor));
            }
        }

        try {
            returnObj = interceptor.afterMethod(target, method, allArguments, null, returnObj);
        } catch (Throwable e) {
            System.err.println(String.format("call afterMethod failed: %s, interceptor: %s", e, interceptor));
        }
        return returnObj;
    }

    public static void onConstructor(Object target, Object[] allArguments, String interceptorClass) {
        Log.info("onConstructor:  target: %s, allArguments: %s, interceptorClass: %s",
                target, Arrays.asList(allArguments), interceptorClass);
    }
}

interface InstanceMethodsInterceptor {
    /**
     * called before target method invocation.
     *
     * @param result change this result, if you want to truncate the method.
     */
    void beforeMethod(Object objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                      MethodInterceptResult result) throws Throwable;

    /**
     * called after target method invocation. Even method's invocation triggers an exception.
     *
     * @param ret the method's original return value. May be null if the method triggers an exception.
     * @return the method's actual return value.
     */
    Object afterMethod(Object objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                       Object ret) throws Throwable;

    /**
     * called when occur exception.
     *
     * @param t the exception occur.
     */
    void handleMethodException(Object objInst, Method method, Object[] allArguments,
                               Class<?>[] argumentsTypes, Throwable t);
}

class MethodInterceptResult {
    private boolean isContinue = true;

    private Object ret = null;

    /**
     * define the new return value.
     *
     * @param ret new return value.
     */
    public void defineReturnValue(Object ret) {
        this.isContinue = false;
        this.ret = ret;
    }

    /**
     * @return true, will trigger method interceptor to invoke
     * the origin method. Otherwise, not.
     */
    public boolean isContinue() {
        return isContinue;
    }

    /**
     * @return the new return value.
     */
    public Object _ret() {
        return ret;
    }
}
