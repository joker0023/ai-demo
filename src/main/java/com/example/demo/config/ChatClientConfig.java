package com.example.demo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.example.demo.service.WeatherTool;

@Configuration
public class ChatClientConfig {
	
	@Autowired
	private VectorStore vectorStore;
	
	@Autowired
	private SessionService sessionService;
	
	@Autowired
	private WeatherTool weatherTool;
	
	private static final double SIMILARITY_THRESHOLD = 0.5;
	
	private static final int TOP_K = 5;
	
//    public ChatClientConfig(VectorStore vectorStore) {
//        this.vectorStore = vectorStore;
//    }

	@Primary
	@Bean
    public ChatClient deepSeekChatClient(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel
    		) {
		//SyncMcpToolCallbackProvider mcpTools
		ToolCallback[] allLocalTools = ToolCallbacks.from(weatherTool);
        return ChatClient.builder(deepSeekChatModel)
        		.defaultTools((Object[]) allLocalTools)
        		.defaultAdvisors(SessionAdvisors.builder(sessionService).build())
        		.build();
    }

    @Bean
    public ChatClient qwenChatClient(@Qualifier("openAiChatModel") ChatModel qwenChatModel) {
        // 可以在这里为千问模型设置一些默认的系统提示词
        return ChatClient.builder(qwenChatModel)
                .defaultSystem("你是一个由阿里云开发的AI助手。")
                .build();
    }
    
    @Bean
    public ChatClient serverChatClient(@Qualifier("deepSeekChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是友好的电商客服顾问，仅基于提供的知识库内容回答用户问题，规则如下：
                        1. 回答需亲切、简洁、准确，符合电商客服沟通语气，避免生硬表述；
                        2. 涉及政策规则时，分点说明关键信息，让用户一目了然；
                        3. 回答末尾必须标注信息来源（格式：信息来源：[文档名称 - 相关条款类别]）；
                        4. 若未查询到相关信息，回复"非常抱歉，暂未查询到该问题的相关规则，建议联系人工客服咨询~"；
                        5. 仅回应与电商购物（退换货、促销、物流等）相关的问题，无关问题直接回复上述统一话术。
                        """)
                .build();
    }

    /**
     * 配置 RetrievalAugmentationAdvisor（适合复杂场景查询，如促销+退换货组合问题）
     */
    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor() {
        // 1. 向量检索器（配置检索参数）
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .topK(TOP_K)
                .build();

        // 查询增强器
        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
		                        .allowEmptyContext(true)
                                .build();
        // 3. 构建检索增强 Advisor
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(queryAugmenter)
                .build();
    }
    
}
