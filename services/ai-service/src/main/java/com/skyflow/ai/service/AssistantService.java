package com.skyflow.ai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.skyflow.ai.config.ClaudeProperties;
import com.skyflow.ai.dto.ChatResponse;
import com.skyflow.ai.dto.ChatTurn;
import com.skyflow.common.exception.UpstreamException;
import com.skyflow.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Support and booking assistant.
 *
 * <p>Runs the tool loop by hand rather than through the SDK's tool runner, because each turn needs
 * the caller's identity threaded into the tool call and a per-conversation iteration cap. The loop
 * is the standard shape: call, execute any {@code tool_use} blocks, hand the results back as a
 * single user message, repeat until the model stops asking for tools.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final String SYSTEM_PROMPT = """
            You are the SkyFlow assistant, helping travellers search flights, understand their
            bookings, and get support. SkyFlow is an airline booking platform.

            How to work:
            - Look things up. Never state a flight, price, seat count or booking detail that did not
              come from a tool result in this conversation. If a tool fails, say so.
            - Resolve city names to airport codes with list_airports when you need to.
            - Keep answers short and concrete. Prices are per seat in USD unless a result says
              otherwise. Times in results are UTC.
            - When you list flights, give the flight number, route, departure time and price, and
              the flight id so the traveller can act on it.

            What you must not do:
            - You cannot create bookings, take payments, change seats, cancel bookings or issue
              refunds. No tool of yours does any of those things. To book, confirm the flight and
              passenger count with the traveller and then use start_booking to give them a checkout
              link. To cancel or refund, tell them to open the booking in "My bookings" and use the
              cancel button there.
            - Never claim an action is done that you only described.
            - You can only see the bookings of the traveller you are talking to. If asked about
              someone else's booking, decline.
            - You are not able to give advice on visas, travel eligibility or health requirements;
              point travellers to the relevant authority instead.
            """;

    private final Optional<AnthropicClient> client;
    private final ClaudeProperties properties;
    private final AssistantTools tools;
    private final ConversationStore conversationStore;

    public AssistantService(Optional<AnthropicClient> client, ClaudeProperties properties,
                            AssistantTools tools, ConversationStore conversationStore) {
        this.client = client;
        this.properties = properties;
        this.tools = tools;
        this.conversationStore = conversationStore;
    }

    public ChatResponse chat(String message, String conversationId, AuthenticatedUser user) {
        AnthropicClient anthropic = client.orElseThrow(() ->
                new UpstreamException("The AI assistant is not configured"));

        String conversation = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId;

        List<ChatTurn> history = conversationStore.load(user.id(), conversation);
        MessageCreateParams.Builder params = baseParams(user);
        for (ChatTurn turn : history) {
            if ("assistant".equals(turn.role())) {
                params.addAssistantMessage(turn.text());
            } else {
                params.addUserMessage(turn.text());
            }
        }
        params.addUserMessage(message);

        Set<String> toolsUsed = new LinkedHashSet<>();
        String reply = runToolLoop(anthropic, params, user, toolsUsed);

        conversationStore.append(user.id(), conversation,
                ChatTurn.user(message), ChatTurn.assistant(reply));
        return new ChatResponse(conversation, reply, List.copyOf(toolsUsed));
    }

    private MessageCreateParams.Builder baseParams(AuthenticatedUser user) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens(properties.getMaxTokens())
                .system(SYSTEM_PROMPT + "\nToday is " + LocalDate.now(ZoneOffset.UTC)
                        + " (UTC). You are talking to the account holder"
                        + (user.email() == null ? "." : " " + user.email() + "."))
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(NaturalLanguageSearchService.effort(properties.getChatEffort()))
                        .build());

        for (Tool tool : tools.definitions()) {
            params.addTool(tool);
        }
        return params;
    }

    private String runToolLoop(AnthropicClient anthropic, MessageCreateParams.Builder params,
                               AuthenticatedUser user, Set<String> toolsUsed) {
        for (int iteration = 0; iteration < properties.getMaxToolIterations(); iteration++) {
            Message response = call(anthropic, params.build());

            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                return "I am not able to help with that. If it is about a booking, "
                        + "opening it in \"My bookings\" is the quickest route.";
            }

            List<ToolUseBlock> toolCalls = response.content().stream()
                    .flatMap(block -> block.toolUse().stream())
                    .toList();

            if (toolCalls.isEmpty()) {
                return textOf(response);
            }

            // The assistant turn (including its tool_use blocks) has to go back verbatim.
            params.addMessage(response.toParam());

            // Every result goes back in ONE user message: splitting them teaches the model to
            // stop making parallel calls.
            List<ContentBlockParam> results = new ArrayList<>();
            for (ToolUseBlock toolCall : toolCalls) {
                toolsUsed.add(toolCall.name());
                results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(toolCall.id())
                        .content(tools.execute(toolCall, user))
                        .build()));
            }
            params.addUserMessageOfBlockParams(results);
        }

        log.warn("Tool loop hit its {}-iteration cap", properties.getMaxToolIterations());
        return "I am still gathering information and did not manage to finish that. "
                + "Could you narrow the question down a little?";
    }

    private static String textOf(Message response) {
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n\n" + right);
        return text.isBlank() ? "Sorry, I did not manage to put together an answer." : text;
    }

    private Message call(AnthropicClient anthropic, MessageCreateParams params) {
        try {
            return anthropic.messages().create(params);
        } catch (AnthropicServiceException ex) {
            log.error("Claude call failed", ex);
            throw new UpstreamException("The AI assistant is temporarily unavailable", ex);
        }
    }
}
