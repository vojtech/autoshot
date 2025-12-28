import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTask

class DokkaConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.dokka")
            }

            subprojects {
                with(pluginManager) {
                    apply("org.jetbrains.dokka")
                }

                tasks.withType<DokkaTask>().configureEach {
                    outputDirectory.set(layout.buildDirectory.dir("dokka"))

                    dokkaSourceSets.configureEach {
                        jdkVersion.set(17)
                        sourceRoots.from(file("src/main/java"), file("src/main/kotlin"))
                    }
                }

                dependencies {
                    "dokkaPlugin"("org.jetbrains.dokka:android-documentation-plugin:2.1.0")
                }
            }

            tasks.withType<DokkaMultiModuleTask>().configureEach {
                outputDirectory.set(layout.buildDirectory.dir("dokka"))
            }
        }
    }
}
