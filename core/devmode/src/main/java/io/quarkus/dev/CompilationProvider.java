package io.quarkus.dev;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

public interface CompilationProvider {

    String handledExtension();

    void compile(Set<File> files, Context context);

    default boolean isCompiledPathModified(final Path resource, Path sourcesDir, Path classesDir, long sourceMod) {
        return false;
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
