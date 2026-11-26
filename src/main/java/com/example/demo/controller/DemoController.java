package com.example.demo.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.demo.dto.DemoReq;
import com.example.demo.service.RagOptimizedService;
import com.example.demo.util.JsonUtil;

import jakarta.annotation.Resource;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai-demo")
public class DemoController {
	
	@Resource(name = "deepSeekChatClient")
    private ChatClient chatClient;
	
	@Resource(name = "serverChatClient")
	private ChatClient serverChatClient;
	
	@Autowired
    private EmbeddingModel embeddingModel;
	
	@Autowired
    private RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
	
	@Autowired
	private ChatClientBuilderCustomizer sessionAdvisorCustomizer;
	
	@Autowired
	private RagOptimizedService ragOptimizedService;

	@GetMapping("/test")
	public Object test(@RequestParam(value = "message", defaultValue = "推荐一款入门级露营装备") String message) {
//		return "success";
		float[] vector = embeddingModel.embed(message);
        // 返回结果（原始文本 + 向量）
        return Map.of(
                "message", message,
                "vectorDimension", vector.length, // 向量维度
                "vector", vector
        );
	}
	
	@PostMapping("/chat")
    public String chat(@RequestBody DemoReq req) {
		System.out.println("msg:" + req.getMessage());
//		Prompt prompt = new Prompt(req.getMessage());
//        return chatModel.call(prompt)
//                .getResult()
//                .getOutput().getText();
		return chatClient.prompt()
                .user(req.getMessage())
                .call()
                .content();
    }
	
	@PostMapping("/answer")
    public String answer(@RequestBody DemoReq req) {
		return ragOptimizedService.answer(req.getMessage());
    }
	
	@PostMapping("/preciseChat")
	public Map<String, String> preciseChat(@RequestBody DemoReq req) {
        String answer = serverChatClient.prompt()
                .user(req.getMessage())
                .advisors(List.of(retrievalAugmentationAdvisor))
                .call()
                .content();
        
        return Map.of(
                "question", req.getMessage(),
                "answer", answer,
                "mode", "precise（精准条款查询）"
        );
    }
	
	@PostMapping("/chatStream")
    public SseEmitter chatStream(@RequestBody DemoReq chatRequest) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        Flux<String> stream = chatClient.prompt()
        		.advisors(SessionAdvisors.session("user-123")) // 更简洁
        		.user(chatRequest.getMessage())
        		.stream().content();

        stream.subscribe(
                chunk -> {
                    try {
                        com.example.demo.dto.ChatResponse chatResponse = new com.example.demo.dto.ChatResponse();
                        chatResponse.setResponse(chunk);
                        chatResponse.setCode(200);
                        chatResponse.setMessage("streaming");
                        
                        String json = JsonUtil.toString(chatResponse);
                        emitter.send(SseEmitter.event()
                                .data(json)
                                .build());
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    emitter.completeWithError(error);
                },
                () -> {
                    emitter.complete();
                }
        );

        return emitter;
    }
}
