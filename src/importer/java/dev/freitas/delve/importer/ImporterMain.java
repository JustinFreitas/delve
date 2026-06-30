package dev.freitas.delve.importer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.freitas.delve.game.dungeon.ModuleSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * CLI entry point for the offline module importer (run via the {@code importModule} Gradle task).
 *
 * <pre>
 *   ./gradlew importModule --args="--pdf=B2.pdf --name=keep"
 *   ./gradlew importModule --args="--text=B2.md --name=keep --model=claude-opus-4-8"
 * </pre>
 *
 * Requires {@code ANTHROPIC_API_KEY}. Writes {@code src/main/resources/content/modules/<name>.json};
 * the result is meant to be reviewed and hand-corrected before play.
 */
public final class ImporterMain {

    private ImporterMain() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        String pdfPath = opts.get("pdf");
        String textPath = opts.get("text");
        String name = opts.get("name");
        String model = opts.getOrDefault("model", "claude-opus-4-8");
        long maxTokens = Long.parseLong(opts.getOrDefault("maxTokens", "32000"));

        if (name == null || name.isBlank() || (pdfPath == null && textPath == null)) {
            System.err.println("Usage: importModule --pdf=<file.pdf>|--text=<file.txt|.md> --name=<module-name>"
                    + " [--model=claude-opus-4-8] [--maxTokens=32000]");
            System.exit(2);
            return;
        }
        if (System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("ANTHROPIC_API_KEY").isBlank()) {
            System.err.println("ANTHROPIC_API_KEY is not set. Export it before running the importer.");
            System.exit(2);
            return;
        }

        String safeName = name.trim().replaceAll("[^A-Za-z0-9_-]", "");
        Path out = opts.containsKey("out")
                ? Path.of(opts.get("out"))
                : Path.of("src", "main", "resources", "content", "modules", safeName + ".json");

        String json;
        if (pdfPath != null) {
            byte[] pdf = Files.readAllBytes(Path.of(pdfPath));
            guardPdf(pdf);
            System.out.println("Converting PDF " + pdfPath + " (" + (pdf.length / 1024) + " KB) with " + model + "...");
            json = ModuleConverter.convertPdf(pdf, model, maxTokens);
        } else {
            String text = Files.readString(Path.of(textPath));
            System.out.println("Converting text " + textPath + " (" + text.length() + " chars) with " + model + "...");
            json = ModuleConverter.convertText(text, model, maxTokens);
        }

        // Validate + pretty-print so the output is reviewable, and fail loudly on a malformed extraction.
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ModuleSchema.Module module = mapper.readValue(json, ModuleSchema.Module.class);
        String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(module);

        Files.createDirectories(out.getParent());
        Files.writeString(out, pretty);

        int rooms = module.levels() == null ? 0
                : module.levels().stream().mapToInt(l -> l.rooms() == null ? 0 : l.rooms().size()).sum();
        System.out.println("Wrote " + out + " — \"" + module.title() + "\", "
                + (module.levels() == null ? 0 : module.levels().size()) + " level(s), " + rooms + " rooms.");
        System.out.println("Review/correct the JSON, then run it in the bot with: enter " + safeName);
    }

    private static void guardPdf(byte[] pdf) throws Exception {
        if (pdf.length > ModuleConverter.MAX_PDF_BYTES) {
            throw new IllegalArgumentException("PDF is " + (pdf.length / (1024 * 1024)) + " MB; the native limit is "
                    + (ModuleConverter.MAX_PDF_BYTES / (1024 * 1024)) + " MB. Split it into page-range PDFs and import each.");
        }
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.getNumberOfPages() > ModuleConverter.MAX_PDF_PAGES) {
                throw new IllegalArgumentException("PDF has " + doc.getNumberOfPages() + " pages; the native limit is "
                        + ModuleConverter.MAX_PDF_PAGES + ". Split it into smaller PDFs and import each.");
            }
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 2) {
                    opts.put(arg.substring(2, eq).toLowerCase(Locale.ROOT), arg.substring(eq + 1));
                } else {
                    opts.put(arg.substring(2).toLowerCase(Locale.ROOT), "true");
                }
            }
        }
        return opts;
    }
}
