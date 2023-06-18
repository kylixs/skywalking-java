package org.apache.skywalking.apm.agent.bytebuddy.case1;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.reflect.Method;

public class AdviceTransformerTest extends AbstractInterceptTest {
    public static void main(String[] args) {
        ByteBuddyAgent.install();

        enableClassDump();

        AgentBuilder.Transformer transformer = new AgentBuilder.Transformer.ForAdvice()
                .include(AdviceTransformerTest.class.getClassLoader()) // 要代理的类
                .advice(ElementMatchers.named("sayHello"), InstMethodAdvice.class.getName()) // 应用的advice
                .advice(ElementMatchers.named("staticMethod"), StaticMethodAdvice.class.getName()); // 应用的advice

        new AgentBuilder.Default()
                .type(ElementMatchers.any()) // 选择所有类型
                .transform(transformer)
                .transform(transformer)
                .with(createListener())
                .installOnByteBuddyAgent(); // 在Byte Buddy Agent上安装

        // 添加其他代理逻辑或启动应用程序

        new MyClass().sayHello("John");
        MyClass.staticMethod("John");
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

    public static class MyClass {
        public String sayHello(String name) {
            // 在这里添加原始方法的实现
            return "HI " + name;
        }

        public static boolean staticMethod(String arg) {
            return false;
        }

    }

    public static class InstMethodAdvice {
        @Advice.OnMethodEnter
        public static void enter(
                @Advice.This Object target,
                @Advice.Origin Method method) {
            System.out.println("进入方法：" + method);
            // 添加在方法进入时执行的逻辑
        }

        @Advice.OnMethodExit
        public static void exit(@Advice.Origin Method method) {
            System.out.println("退出方法：" + method);
            // 添加在方法退出时执行的逻辑
        }
    }

    public static class StaticMethodAdvice {
        @Advice.OnMethodEnter(inline = false)
        public static void enter(
                @Advice.Origin Method method) {
            System.out.println("进入方法：" + method);
            // 添加在方法进入时执行的逻辑
        }

        @Advice.OnMethodExit
        public static void exit(@Advice.Origin Method method) {
            System.out.println("退出方法：" + method);
            // 添加在方法退出时执行的逻辑
        }
    }

}
