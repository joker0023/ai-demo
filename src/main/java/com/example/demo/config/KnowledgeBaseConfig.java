package com.example.demo.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeBaseConfig {
	
	private static final String META_SOURCE = "source";

    private static final String META_CHUNK_INDEX = "chunkIndex";
    
    private static final String KNOWLEDGE_SOURCE = "电商知识库标准条款.doc";
	
    private final VectorStore vectorStore;
    
    public KnowledgeBaseConfig(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 项目启动时初始化知识库：解析 PDF 文档 → 切分文本 → 向量入库
     */
    @PostConstruct
    public void initKnowledgeBase() {
        try {
            System.out.println("开始初始化电商客服知识库...");
            
            // 1. 定义需要导入的 PDF 文档列表
            List<String> pdfFiles = List.of(KNOWLEDGE_SOURCE);

            List<Document> allSplitDocs = new ArrayList<>();
            for (String fileName : pdfFiles) {
                // 2. 读取单个 PDF 文档
                Resource resource = new ClassPathResource(fileName);
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                List<Document> rawDocs = reader.read();

                // 3. 优化文本切分策略（适配电商规则条款化特点）
                TokenTextSplitter splitter = TokenTextSplitter.builder()
                        .withChunkSize(600)          // 每段最大 600 Token（匹配规则条款长度）
                        .withMinChunkSizeChars(200)  // 每段最小 200 字符
                        .withKeepSeparator(true)     // 保留条款分隔符
                        .build();

                // 4. 切分当前文档并添加到总列表
                List<Document> splitDocs = splitter.apply(rawDocs);
                allSplitDocs.addAll(splitDocs);
                System.out.println("已解析文档：" + fileName + "，生成 " + splitDocs.size() + " 个文本片段");
            }
            
            //////////////////////////////////
            /// 

            // 3. 幂等写入：先删除本知识库上次写入的片段
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            vectorStore.delete(fb.eq(META_SOURCE, KNOWLEDGE_SOURCE).build());

            // 4. 给每个片段打上 metadata（source + chunkIndex），再写入 Milvus
            List<Document> toStore = new ArrayList<>();
            int chunkIndex = 0;
            for (Document doc : allSplitDocs) {
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
            
            System.out.println("知识库初始化完成，共导入 " + allSplitDocs.size() + " 个文本片段");
        } catch (Exception e) {
            System.err.println("知识库初始化失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
