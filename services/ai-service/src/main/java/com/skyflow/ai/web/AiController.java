package com.skyflow.ai.web;

import com.skyflow.ai.dto.ChatRequest;
import com.skyflow.ai.dto.ChatResponse;
import com.skyflow.ai.dto.NlSearchRequest;
import com.skyflow.ai.dto.NlSearchResponse;
import com.skyflow.ai.service.AssistantService;
import com.skyflow.ai.service.ConversationStore;
import com.skyflow.ai.service.NaturalLanguageSearchService;
import com.skyflow.common.api.ApiResponse;
import com.skyflow.common.security.AuthenticatedUser;
import com.skyflow.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final NaturalLanguageSearchService searchService;
    private final AssistantService assistantService;
    private final ConversationStore conversationStore;

    public AiController(NaturalLanguageSearchService searchService, AssistantService assistantService,
                        ConversationStore conversationStore) {
        this.searchService = searchService;
        this.assistantService = assistantService;
        this.conversationStore = conversationStore;
    }

    /**
     * Natural language flight search. Open to anonymous visitors: it reads public schedule data
     * and is the entry point on the landing page.
     */
    @PostMapping("/search")
    public ApiResponse<NlSearchResponse> search(@Valid @RequestBody NlSearchRequest request) {
        return ApiResponse.success(searchService.search(request.query()), "Search interpreted");
    }

    /** Support and booking chat. Requires a signed-in traveller: the tools read their bookings. */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                          @CurrentUser AuthenticatedUser user) {
        return ApiResponse.success(
                assistantService.chat(request.message(), request.conversationId(), user), "OK");
    }

    @DeleteMapping("/chat/{conversationId}")
    public ApiResponse<Void> clear(@PathVariable String conversationId,
                                   @CurrentUser AuthenticatedUser user) {
        conversationStore.clear(user.id(), conversationId);
        return ApiResponse.success(null, "Conversation cleared");
    }
}
