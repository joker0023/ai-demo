package com.example.demo.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class RagOptimizedService {

    /** 知识库文件（同时作为 metadata 里的 source 标识） */
    private static final String KNOWLEDGE_SOURCE = "户外旅行安全指南.txt";

    /** 检索返回的最大片段数 */
    private static final int TOP_K = 5;

    /** 相似度阈值（余弦相似度，按实际 embedding 模型效果调整） */
    private static final double SIMILARITY_THRESHOLD = 0.5;

    private static final String META_SOURCE = "source";

    private static final String META_CHUNK_INDEX = "chunkIndex";

    private final ChatClient chatClient;

    private final VectorStore vectorStore;

    // 构造方法注入依赖（向量化/写入/检索都交给 VectorStore 完成）
    public RagOptimizedService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    /**
     * 项目启动时初始化知识库：加载、切分、写入 Milvus。
     * 写入是幂等的：先按 source 删除上一次的片段，再重新写入，避免每次重启重复累积向量。
     */
    @PostConstruct
    public void initKnowledgeBase() throws IOException {
        System.out.println("开始初始化知识库，加载文件并写入 Milvus 向量数据库...");

        // 1. 加载本地知识库文件（户外旅行安全指南）
        Resource resource = new ClassPathResource(KNOWLEDGE_SOURCE);
        List<Document> rawDocs = new TextReader(resource).read();

        // 2. 按语义切分文本
        TokenTextSplitter textSplitter = TokenTextSplitter.builder()
                .withChunkSize(800)          // 每段最大 800 Token
                .withMinChunkSizeChars(400)  // 每段最小 400 字符（避免过短）
                .withKeepSeparator(true)     // 保留分隔符，提升语义连贯性
                .build();
        List<Document> splitDocs = textSplitter.apply(rawDocs);

        // 3. 幂等写入：先删除本知识库上次写入的片段
        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        vectorStore.delete(fb.eq(META_SOURCE, KNOWLEDGE_SOURCE).build());

        // 4. 给每个片段打上 metadata（source + chunkIndex），再写入 Milvus
        List<Document> toStore = new ArrayList<>();
        int chunkIndex = 0;
        for (Document doc : splitDocs) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            toStore.add(new Document(text.strip(), Map.of(
                    META_SOURCE, KNOWLEDGE_SOURCE,
                    META_CHUNK_INDEX, chunkIndex)));
            chunkIndex++;
        }
        if (!toStore.isEmpty()) {
            // VectorStore.add 内部会调用 EmbeddingModel 向量化，然后写入 Milvus
            vectorStore.add(toStore);
        }
        System.out.println("知识库初始化完成，共写入 " + toStore.size() + " 个文本片段");
    }

    /**
     * 处理用户提问，返回优化后的 RAG 回答
     * @param question 用户问题
     * @return 大模型生成的回答
     */
    public String answer(String question) {
        // 1. 在 Milvus 中按向量相似度检索 TopK 片段（检索真正走向量数据库）
        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .filterExpression(fb.eq(META_SOURCE, KNOWLEDGE_SOURCE).build())
                .build());
        System.out.println("Milvus 检索到 " + hits.size() + " 个高相关片段：" + hits.stream()
                .map(doc -> doc.getMetadata().get(META_CHUNK_INDEX) + "(score=" + doc.getScore() + ")")
                .collect(Collectors.joining(", ")));

        // 2. 利用 metadata 里的 chunkIndex 扩展前后相邻片段，提升上下文连贯性
        List<Document> contextDocs = expandWithNeighbors(hits, question);

        // 3. 按文档顺序拼接上下文（去重）
        String context = contextDocs.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("\n---\n"));

        // 4. 构建提示词（明确要求基于上下文回答，禁止添加额外信息）
        String prompt = String.format(
                "以下是户外旅行安全指南的知识库内容：\n%s\n请严格基于上述内容，简洁、准确地回答用户问题，不要添加额外信息。问题：%s",
                context, question
        );

        // 5. 调用 Chat 模型生成回答
        return chatClient.prompt()
                .system("你是专业的户外旅行安全助手，仅基于提供的上下文回答问题，若上下文无相关信息，回复'抱歉，未查询到相关安全指南信息'。")
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 根据检索结果的 chunkIndex，用一次带 metadata 过滤条件的 Milvus 检索把前后相邻片段一并取出。
     * 如果检索结果本身没有 chunkIndex 元数据（例如旧数据），则直接返回原始结果。
     * @param hits 向量检索得到的 TopK 片段
     * @param question 用户问题（用于第二次向量检索）
     * @return 按 chunkIndex 升序排列的片段列表
     */
    private List<Document> expandWithNeighbors(List<Document> hits, String question) {
        Map<Integer, Document> docsByIndex = new HashMap<>();
        Set<Integer> hitIndexes = new HashSet<>();
        for (Document doc : hits) {
            Integer index = chunkIndexOf(doc);
            if (index != null) {
                docsByIndex.put(index, doc);
                hitIndexes.add(index);
            }
        }
        if (hitIndexes.isEmpty()) {
            return hits;
        }

        // 1. 高相关片段 + 前后各 1 个相邻片段
        Set<Integer> targetIndexes = new TreeSet<>();
        for (Integer index : hitIndexes) {
            targetIndexes.add(index);
            if (index > 0) {
                targetIndexes.add(index - 1);
            }
            targetIndexes.add(index + 1);
        }

        // 2. 只查还没有拿到的相邻片段
        Set<Integer> missingIndexes = new TreeSet<>(targetIndexes);
        missingIndexes.removeAll(docsByIndex.keySet());

        if (!missingIndexes.isEmpty()) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            Filter.Expression neighborFilter = fb.and(
                    fb.eq(META_SOURCE, KNOWLEDGE_SOURCE),
                    fb.in(META_CHUNK_INDEX, missingIndexes.toArray())).build();
            List<Document> neighbors = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(missingIndexes.size())
                    .similarityThresholdAll() // 相邻片段是被显式指定的，不做相似度过滤
                    .filterExpression(neighborFilter)
                    .build());
            for (Document doc : neighbors) {
                Integer index = chunkIndexOf(doc);
                if (index != null) {
                    docsByIndex.putIfAbsent(index, doc);
                }
            }
        }

        // 3. 按 chunkIndex 升序返回，保证上下文与原文顺序一致
        return docsByIndex.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private Integer chunkIndexOf(Document doc) {
        Object value = doc.getMetadata().get(META_CHUNK_INDEX);
        return value instanceof Number number ? number.intValue() : null;
    }
}
