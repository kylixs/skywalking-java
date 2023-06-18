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

package org.apache.skywalking.apm.agent.bytebuddy.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BytecodeUtils {

    private static Map<ClassLoader, CustomClassLoader> CUSTOM_CLASSLOADER_CACHE = new ConcurrentHashMap<>();

    private static Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    private static Map<String, byte[]> CLASS_BINARY_CACHE = new ConcurrentHashMap<>();

    public static Class<?> replaceConstant(Class<?> originalClass, String constantTag, Object newValue, String newClassName) throws Exception {
        ClassReader classReader = new ClassReader(originalClass.getName());
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM7, classWriter) {

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                String newInternalName = newClassName.replace('.', '/');
                super.visit(version, access, newInternalName, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM7, methodVisitor) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        // replace constant inside method
                        if (value != null && value.equals(constantTag)) {
                            value = newValue;
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                // replace constant field value
                if (value != null && constantTag.equals(value)) {
                    value = newValue;
                }
                return super.visitField(access, name, descriptor, signature, value);
            }
        };

        // make new class binary
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        byte[] modifiedClassBytes = classWriter.toByteArray();

        // load new class
        ClassLoader classLoader = originalClass.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        CustomClassLoader customClassLoader = CUSTOM_CLASSLOADER_CACHE.computeIfAbsent(classLoader, CustomClassLoader::new);
        Class<?> definedClass = customClassLoader.defineClass(newClassName, modifiedClassBytes);
        CLASS_CACHE.put(newClassName, definedClass);
        CLASS_BINARY_CACHE.put(newClassName, modifiedClassBytes);
        return definedClass;
    }

    public static Map<String, Class<?>> getClassCache() {
        return CLASS_CACHE;
    }

    public static byte[] getClassBinary(String className) {
        return CLASS_BINARY_CACHE.get(className);
    }

    public static class CustomClassLoader extends ClassLoader {

        public static final String CLASS_EXTENSION = ".class";

        public CustomClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (name.endsWith(CLASS_EXTENSION)) {
                String className = name.replace('/', '.');
                className = className.substring(0, className.length() - CLASS_EXTENSION.length());
                byte[] classBinary = getClassBinary(className);
                if (classBinary != null) {
                    return new ByteArrayInputStream(classBinary);
                }
            }
            return super.getResourceAsStream(name);
        }
    }
}