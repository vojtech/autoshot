import com.android.build.api.dsl.CommonExtension
import com.fediim.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            val extension = extensions.findByType(CommonExtension::class.java)
                ?: throw IllegalStateException("Could not find Android extension")

            with(extension) {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                val bom = libs.findLibrary("androidx.compose.bom").get()
                "implementation"(platform(bom))
                "implementation"(libs.findLibrary("androidx.ui").get())
                "implementation"(libs.findLibrary("androidx.ui.graphics").get())
                "implementation"(libs.findLibrary("androidx.ui.tooling.preview").get())
                "implementation"(libs.findLibrary("androidx.material3").get())
                "debugImplementation"(libs.findLibrary("androidx.ui.tooling").get())
                "debugImplementation"(libs.findLibrary("androidx.ui.test.manifest").get())
            }
        }
    }
}
