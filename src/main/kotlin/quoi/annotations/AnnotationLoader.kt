package quoi.annotations

import io.github.classgraph.ClassGraph
import quoi.QuoiMod.logger

object AnnotationLoader {
    fun load() {
        ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptPackages("quoi")
            .scan()
            .use { scan ->
                scan.getClassesWithAnnotation(Init::class.java.name)
                    .loadClasses()
                    .sortedByDescending { it.getAnnotation(Init::class.java)?.priority ?: 0 }
                    .forEach { clazz ->
                        runCatching {
                            clazz.getField("INSTANCE").get(null)
                        }.recoverCatching {
                            Class.forName(clazz.name, true, clazz.classLoader)
                        }.getOrElse { e -> logger.error("Failed to load ${clazz.name}"); e.printStackTrace() }
                    }
            }
    }
}