package io.quarkus.drools.deployment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.kie.submarine.codegen.ApplicationGenerator;
import org.kie.submarine.codegen.GeneratedFile;
import org.kie.submarine.codegen.process.ProcessCodegen;

import io.quarkus.dev.JavaCompilationProvider;

public class KieCompilationProvider extends JavaCompilationProvider {

    @Override
    public String handledExtension() {
        return ".bpmn";
    }

    @Override
    public boolean resourceModified(Path resource, Path sourcesDir, Path classesDir, String matchingExtension, long sourceMod) {
        return false;
    }

    @Override
    public void compile(Set<File> filesToCompile, Context context) {
        String appPackageName = "org.kie";
        File outputDirectory = context.getOutputDirectory();
        try {

            ApplicationGenerator appGen = new ApplicationGenerator(appPackageName, outputDirectory)
                    .withDependencyInjection(true);
            appGen.withGenerator(
                    ProcessCodegen.ofFiles(new ArrayList<>(filesToCompile))); //fixme in submarine-codegen: should accept any collection

            Collection<GeneratedFile> generatedFiles = appGen.generate();

            HashSet<File> generatedSourceFiles = new HashSet<>();
            for (GeneratedFile file : generatedFiles) {
                Path path = pathOf(outputDirectory.getPath(), file.relativePath());
                Files.write(path, file.contents());
                generatedSourceFiles.add(path.toFile());
            }
            super.compile(generatedSourceFiles, context);
        } catch (IOException e) {
            throw new KieCompilerException(e);
        }
    }

    private Path pathOf(String path, String relativePath) {
        Path p = Paths.get(path, relativePath);
        p.getParent().toFile().mkdirs();
        return p;
    }
}
