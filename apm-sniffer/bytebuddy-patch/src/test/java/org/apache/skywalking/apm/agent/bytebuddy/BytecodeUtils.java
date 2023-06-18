package org.apache.skywalking.apm.agent.bytebuddy;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


public class BytecodeUtils {

    public static Class<?> replaceConstant(Class<?> originalClass, String constantTag, Object newValue, String newClassName) throws Exception {
        // 读取原始类的字节码
        ClassReader classReader = new ClassReader(originalClass.getName());
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);

        // 创建自定义的类访问器
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM7, classWriter) {

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                String newInternalName = newClassName.replace('.', '/');
                super.visit(version, access, newInternalName, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                // 查找需要替换的方法
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                // 创建新的方法访问器，用于替换常量标记
                return new MethodVisitor(Opcodes.ASM7, methodVisitor) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        // 将原始的常量标记替换为新的值
                        if (value != null && value.equals(constantTag)) {
                            value = newValue;
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (value != null && constantTag.equals(value)) {
                    value = newValue;
                }
                return super.visitField(access, name, descriptor, signature, value);
            }
        };

        // 修改字节码并获取修改后的字节数组
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        byte[] modifiedClassBytes = classWriter.toByteArray();

        // 使用原始类的类加载器加载修改后的字节码
        CustomClassLoader classLoader = new CustomClassLoader(originalClass.getClassLoader());
        return classLoader.defineClass(newClassName, modifiedClassBytes);
    }

    public static class CustomClassLoader extends ClassLoader {
        public CustomClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }
}