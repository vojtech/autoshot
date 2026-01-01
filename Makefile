.PHONY: publish-plugins publish-processor publish-all

# Publish all build-logic plugins to Maven Local
publish-plugins:
	./gradlew \
	:build-logic:android-application-plugin:publishToMavenLocal \
	:build-logic:android-library-plugin:publishToMavenLocal \
	:build-logic:android-compose-plugin:publishToMavenLocal \
	:build-logic:dokka-plugin:publishToMavenLocal \
	:build-logic:screenshot-plugin:publishToMavenLocal

# Publish the KSP processor to Maven Local
publish-processor:
	./gradlew :processor:publishToMavenLocal

# Publish the KSP Annotation to Maven Local
publish-annotation:
	./gradlew :annotation:publishToMavenLocal

# Publish everything
publish-all: publish-processor publish-annotation publish-plugins

# Apply Spotless formatting
format:
	./gradlew --init-script gradle/init.gradle.kts spotlessApply
