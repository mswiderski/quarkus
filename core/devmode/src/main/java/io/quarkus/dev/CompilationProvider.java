package io.quarkus.dev;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public interface CompilationProvider {

    String handledExtension();

    void compile(Set<File> files, Context context);

    default boolean resourceModified(final Path resource, Path sourcesDir, Path classesDir, String matchingExtension,
            long sourceMod) {
        String pathName = sourcesDir.relativize(resource).toString();
        String classFileName = pathName.substring(0, pathName.length() - matchingExtension.length()) + ".class";
        Path classFile = classesDir.resolve(classFileName);
        if (!Files.exists(classFile)) {
            return true;
        }
        try {
            return sourceMod > Files.getLastModifiedTime(classFile).toMillis();
        } catch (IOException e) {
            return false;
        }
    }

    class Context {
        private final Set<File> classpath;
        private final File outputDirectory;

        public Context(Set<File> classpath, File outputDirectory) {
            this.classpath = classpath;
            this.outputDirectory = outputDirectory;
        }

        public Set<File> getClasspath() {
            return classpath;
        }

        public File getOutputDirectory() {
            return outputDirectory;
        }
    }
}
