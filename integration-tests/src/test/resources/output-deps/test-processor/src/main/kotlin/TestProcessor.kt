import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.OutputStreamWriter

class TestProcessor : SymbolProcessor {
    lateinit var codeGenerator: CodeGenerator
    lateinit var logger: KSPLogger
    var processed = false

    fun init(
        options: Map<String, String>,
        kotlinVersion: KotlinVersion,
        codeGenerator: CodeGenerator,
        logger: KSPLogger,
    ) {
        this.codeGenerator = codeGenerator
        this.logger = logger
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) {
            return emptyList()
        }
        fun outputForAnno(anno: String) {
            val annoFiles =
                resolver.getSymbolsWithAnnotation(anno).map { (it as KSDeclaration).containingFile!! }.toList()
            codeGenerator.createNewFile(Dependencies(false, *annoFiles.toTypedArray()), "", anno, "log").use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(annoFiles.map { it.fileName }.joinToString(", "))
                }
            }
        }

        outputForAnno("p1.Anno1")
        outputForAnno("p1.Anno2")

        resolver.getNewFiles().forEach { file ->
            logger.warn("${file.packageName.asString()}/${file.fileName}")
            val outputBaseFN = file.fileName.replace(".kt", "Generated").replace(".java", "Generated")
            codeGenerator.createNewFile(Dependencies(false, file), file.packageName.asString(), outputBaseFN, "kt")
                .use { output ->
                    OutputStreamWriter(output).use { writer ->
                        writer.write("private val unused = \"unused\"")
                    }
                }
        }
        processed = true
        return emptyList()
    }
}

class TestProcessorProvider : SymbolProcessorProvider {
    override fun create(
        env: SymbolProcessorEnvironment,
    ): SymbolProcessor {
        return TestProcessor().apply {
            init(env.options, env.kotlinVersion, env.codeGenerator, env.logger)
        }
    }
}
