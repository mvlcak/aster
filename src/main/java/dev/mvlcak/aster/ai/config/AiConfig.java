package dev.mvlcak.aster.ai.config;

import dev.mvlcak.aster.ai.tool.DiffTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Schedulers;

@Configuration
public class AiConfig {

	@Bean
	public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
		return MessageChatMemoryAdvisor.builder(chatMemory)
				.scheduler(Schedulers.boundedElastic())
				.build();
	}

	@Bean
	public ChatMemoryRepository chatMemoryRepository() {
		return new InMemoryChatMemoryRepository();
	}

	@Bean
	public ChatClient chatClient(ChatModel chatModel,
	                             GrepTool grepTool,
	                             FileSystemTools fileSystemTools,
	                             ShellTools shellTools,
	                             MessageChatMemoryAdvisor messageChatMemoryAdvisor,
	                             DiffTool diffTool,
	                             ObjectProvider<SyncMcpToolCallbackProvider> mcpTools) {

		ChatClient.Builder builder = ChatClient.builder(chatModel)
				.defaultSystem("""
						You are a helpful coding assistant named Aster. You have access to tools
						for reading files, searching code, running shell commands.

						Current directory: %s
						""".formatted(System.getProperty("user.dir")))
				.defaultAdvisors(messageChatMemoryAdvisor)
				.defaultTools(grepTool, fileSystemTools, shellTools, diffTool);

		mcpTools.ifAvailable(builder::defaultTools);

		return builder.build();
	}

}
