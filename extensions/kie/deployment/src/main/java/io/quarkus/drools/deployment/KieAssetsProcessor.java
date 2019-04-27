/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quarkus.drools.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.drools.compiler.commons.jci.compilers.CompilationResult;
import org.drools.compiler.commons.jci.compilers.JavaCompiler;
import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.compiler.rule.builder.dialect.java.JavaDialectConfiguration;
import org.drools.modelcompiler.builder.JavaParserCompiler;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.kie.submarine.codegen.ApplicationGenerator;
import org.kie.submarine.codegen.GeneratedFile;
import org.kie.submarine.codegen.process.ProcessCodegen;
import org.kie.submarine.codegen.rules.RuleCodegen;

import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ArchiveRootBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.quarkus.undertow.deployment.ServletInitParamBuildItem;

public class KieAssetsProcessor {

    @BuildStep(providesCapabilities = "io.quarkus.kie")
    FeatureBuildItem featureBuildItem() {
        return new FeatureBuildItem("kie");
    }

    @BuildStep
    @Record(STATIC_INIT)
    public void generateModel(ArchiveRootBuildItem root,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<ServletInitParamBuildItem> servletContextParams) throws IOException {

        boolean generateRuleUnits = true;
        boolean generateProcesses = true;

        ApplicationGenerator appGen = createApplicationGenerator(root,
                generateRuleUnits,
                generateProcesses);

        Collection<GeneratedFile> generatedFiles = appGen.generate();

        compileAndRegister(generatedFiles, generatedBeans, generatedClasses);

        Set<String> resources = new HashSet<>();

        for (GeneratedFile entry : generatedFiles) {
            String className = toClassName(entry.relativePath());
            if (entry.getType().equals(GeneratedFile.Type.REST)) {
                reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, className));
                resources.add(className);
            }
        }
        if (!resources.isEmpty()) {
            servletContextParams.produce(
                    new ServletInitParamBuildItem(ResteasyContextParameters.RESTEASY_SCANNED_RESOURCES,
                            String.join(",", resources)));
        }

    }

    private void compileAndRegister(Collection<GeneratedFile> generatedFiles,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans, BuildProducer<GeneratedClassBuildItem> generatedClasses) {
        if (generatedFiles.isEmpty()) {
            return;
        }

        JavaCompiler javaCompiler = JavaParserCompiler.getCompiler(JavaDialectConfiguration.CompilerType.ECLIPSE);

        MemoryFileSystem srcMfs = new MemoryFileSystem();
        MemoryFileSystem trgMfs = new MemoryFileSystem();

        String[] sources = new String[generatedFiles.size()];
        int index = 0;
        for (GeneratedFile entry : generatedFiles) {
            String fileName = toRuntimeSource(toClassName(entry.relativePath()));
            sources[index++] = fileName;

            srcMfs.write(fileName, entry.contents());
        }

        CompilationResult result = javaCompiler.compile(sources, srcMfs, trgMfs, this.getClass().getClassLoader());

        if (result.getErrors().length > 0) {

            StringBuilder errorInfo = new StringBuilder();
            Arrays.stream(result.getErrors()).forEach(cp -> errorInfo.append(cp.toString()));
            throw new IllegalStateException(errorInfo.toString());

        }

        for (String fileName : trgMfs.getFileNames()) {
            generatedBeans.produce(new GeneratedBeanBuildItem(toClassName(fileName), trgMfs.getBytes(fileName)));

        }
    }

    private ApplicationGenerator createApplicationGenerator(ArchiveRootBuildItem root,
            boolean generateRuleUnits,
            boolean generateProcesses) throws IOException {
        Path targetClassesPath = root.getPath();
        Path projectPath = targetClassesPath.toString().endsWith("target/classes") ? targetClassesPath.getParent().getParent()
                : targetClassesPath;

        String appPackageName = "org.kie";

        ApplicationGenerator appGen = new ApplicationGenerator(appPackageName, new File(projectPath.toFile(), "target"))
                .withDependencyInjection(true);

        if (generateRuleUnits) {
            appGen.withGenerator(RuleCodegen.ofPath(projectPath));
        }

        if (generateProcesses) {
            appGen.withGenerator(ProcessCodegen.ofPath(projectPath))
                    .withWorkItemHandlerConfig(
                            customWorkItemConfigExists(projectPath, appPackageName))
                    .withProcessEventListenerConfig(
                            customProcessListenerConfigExists(projectPath, appPackageName));
        }

        return appGen;
    }

    private String customWorkItemConfigExists(Path projectPath, String appPackageName) {
        String sourceDir = Paths.get(projectPath.toString(), "src").toString();
        String workItemHandlerConfigClass = ProcessCodegen.defaultWorkItemHandlerConfigClass(appPackageName);
        Path p = Paths.get(sourceDir,
                "main/java",
                workItemHandlerConfigClass.replace('.', '/') + ".java");
        return Files.exists(p) ? workItemHandlerConfigClass : null;
    }

    private String customProcessListenerConfigExists(Path projectPath, String appPackageName) {
        String sourceDir = Paths.get(projectPath.toString(), "src").toString();
        String processEventListenerClass = ProcessCodegen.defaultProcessListenerConfigClass(appPackageName);
        Path p = Paths.get(sourceDir,
                "main/java",
                processEventListenerClass.replace('.', '/') + ".java");
        return Files.exists(p) ? processEventListenerClass : null;
    }

    private String toRuntimeSource(String className) {
        return "src/main/java/" + className.replace('.', '/') + ".java";
    }

    private String toClassName(String sourceName) {
        if (sourceName.startsWith("./")) {
            sourceName = sourceName.substring(2);
        }
        if (sourceName.endsWith(".java")) {
            sourceName = sourceName.substring(0, sourceName.length() - 5);
        } else if (sourceName.endsWith(".class")) {
            sourceName = sourceName.substring(0, sourceName.length() - 6);
        }
        return sourceName.replace('/', '.');
    }
}
