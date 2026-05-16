package gr.uoa.di.madgik.ChartDataFormatter.nl.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider nlToolCallbackProvider(NlMcpTools nlMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(nlMcpTools)
                .build();
    }
}
