plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
val poiOriginal by configurations.creating { isTransitive = false }
dependencies {
    poiOriginal("org.apache.poi:poi:5.5.1")
    api("commons-codec:commons-codec:1.20.0")
    api("org.apache.commons:commons-collections4:4.5.0")
    api("org.apache.commons:commons-math3:3.6.1")
    api("commons-io:commons-io:2.21.0")
    api("com.zaxxer:SparseBitSet:1.3")
    api("org.apache.logging.log4j:log4j-api:2.24.3")
}
interface OfficeFileOperations {
    @get:javax.inject.Inject
    val fileSystem: org.gradle.api.file.FileSystemOperations
}

// Android consumes a project's class directories for dexing, not necessarily
// its jar. Put the pinned upstream classes into that declared output as well.
tasks.compileJava {
    // Capture supported task data and an injected service, not the build script
    // or Configuration, in the execution-time action.
    val operations = objects.newInstance(OfficeFileOperations::class.java)
    val upstreamEntries = poiOriginal.files.map { zipTree(it) }
    val classOutput = destinationDirectory
    inputs.files(poiOriginal)
    doLast {
        operations.fileSystem.copy {
            from(upstreamEntries)
            into(classOutput)
            exclude("org/apache/poi/poifs/nio/CleanerUtil*.class")
            exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        }
    }
}
