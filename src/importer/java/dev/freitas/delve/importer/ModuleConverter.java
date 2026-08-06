package dev.freitas.delve.importer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.util.Base64;
import java.util.List;

/**
 * Offline converter: sends a B/X module (a FineReader searchable PDF, or a plain-text/Markdown export)
 * to Gemini (default) or Claude and gets back a {@code module.json} matching delve's authored schema.
 * Runs only as part of the {@code importModule} Gradle task — never in the bot.
 */
final class ModuleConverter {

    // Native PDF input limits: 32 MB request, 600 pages.
    static final int MAX_PDF_BYTES = 32 * 1024 * 1024;
    static final int MAX_PDF_PAGES = 600;

    private ModuleConverter() {}

    /** Converts a PDF byte array to a module JSON string. */
    static String convertPdf(String provider, byte[] pdf, String model, long maxTokens) {
        if ("claude".equalsIgnoreCase(provider)) {
            return convertPdfClaude(pdf, model, maxTokens);
        }
        return convertPdfGemini(pdf, model);
    }

    /** Converts a plain-text/Markdown export to a module JSON string. */
    static String convertText(String provider, String moduleText, String model, long maxTokens) {
        if ("claude".equalsIgnoreCase(provider)) {
            return convertTextClaude(moduleText, model, maxTokens);
        }
        return convertTextGemini(moduleText, model);
    }

    // --- GEMINI IMPL ---

    private static String convertPdfGemini(byte[] pdf, String model) {
        String modelName = (model == null || model.isBlank() || model.equals("claude-opus-4-8")) ? "gemini-2.5-flash" : model;
        Client client = Client.builder().build();
        Part pdfPart = Part.fromBytes(pdf, "application/pdf");
        Part promptPart = Part.fromText(PROMPT);
        Content content = Content.builder().parts(List.of(pdfPart, promptPart)).build();
        GenerateContentResponse response = client.models.generateContent(modelName, content, null);
        return extractJson(response.text());
    }

    private static String convertTextGemini(String moduleText, String model) {
        String modelName = (model == null || model.isBlank() || model.equals("claude-opus-4-8")) ? "gemini-2.5-flash" : model;
        Client client = Client.builder().build();
        String fullPrompt = PROMPT + "\n\n--- MODULE TEXT BELOW ---\n\n" + moduleText;
        GenerateContentResponse response = client.models.generateContent(modelName, fullPrompt, null);
        return extractJson(response.text());
    }

    // --- CLAUDE IMPL ---

    private static String convertPdfClaude(byte[] pdf, String model, long maxTokens) {
        String base64 = Base64.getEncoder().encodeToString(pdf);
        DocumentBlockParam doc = DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(base64).build())
                .build();
        MessageCreateParams params = baseBuilderClaude(model, maxTokens)
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofDocument(doc),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build())))
                .build();
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();
        Message response = client.messages().create(params);
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().map(TextBlock::text).ifPresent(sb::append);
        }
        return extractJson(sb.toString());
    }

    private static String convertTextClaude(String moduleText, String model, long maxTokens) {
        MessageCreateParams params = baseBuilderClaude(model, maxTokens)
                .addUserMessage(PROMPT + "\n\n--- MODULE TEXT BELOW ---\n\n" + moduleText)
                .build();
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();
        Message response = client.messages().create(params);
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().map(TextBlock::text).ifPresent(sb::append);
        }
        return extractJson(sb.toString());
    }

    private static MessageCreateParams.Builder baseBuilderClaude(String model, long maxTokens) {
        return MessageCreateParams.builder()
                .model(model == null || model.isBlank() ? "claude-opus-4-8" : model)
                .maxTokens(maxTokens)
                .thinking(ThinkingConfigAdaptive.builder().build());
    }

    /** Trims raw model response text to the outermost JSON object. */
    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Model did not return a JSON object. Raw response:\n" + text);
        }
        return text.substring(start, end + 1);
    }

    private static final String PROMPT =
            """
            You are converting a Basic/Expert (B/X) Dungeons & Dragons adventure module into a structured
            JSON file for a text-based dungeon-crawler. Read the module (its keyed area descriptions,
            stat blocks, and maps) and extract it faithfully.

            Return ONLY a single JSON object (no prose, no markdown fences) with this exact shape:

            {
              "title": "<module title>",
              "levels": [
                {
                  "depth": 1,
                  "entranceRoomId": <id of the starting/entrance area>,
                  "rooms": [
                    {
                      "id": <the area/room number as printed>,
                      "name": "Area <n>: <short name>",
                      "description": "<one-line scene the DM sees>",
                      "readAloud": "<boxed read-aloud text, if any; else omit>",
                      "exits": [
                        { "direction": "north|south|east|west", "toRoomId": <area number>, "door": "open|closed|stuck|locked", "secret": false }
                      ],
                      "monster": { "name": "<creature>", "count": <n> },
                      "treasureGold": <gp value of treasure here, 0 if none>,
                      "trap": { "description": "<trap, if any>" },
                      "special": "<notable feature, if any>",
                      "stairsDown": false,
                      "stairsUp": false,
                      "stairsToLevel": <0-based index of destination level, if stairs>,
                      "stairsToRoom": <destination area id, if stairs>
                    }
                  ]
                }
              ]
            }

            Rules:
            - One "rooms" entry per keyed area, using the printed area numbers as ids.
            - Infer "exits" from each area's text ("a door in the north wall leads to area 7" ->
              {direction:"north", toRoomId:7, door:"closed"}). Only cardinal directions; if the text
              gives no direction, choose a plausible unused one. Mark secret doors with "secret": true.
            - For multi-level dungeons, emit one "levels" entry per dungeon level (depth 1,2,3...),
              and connect them with stairsDown/stairsUp + stairsToLevel/stairsToRoom.
            - Use B/X creature names where possible (Goblin, Orc, Skeleton, Hobgoblin, Zombie, etc.).
            - Omit fields that don't apply (no monster, no trap, no readAloud) rather than inventing them.
            - Output valid JSON and nothing else.
            """;
}
