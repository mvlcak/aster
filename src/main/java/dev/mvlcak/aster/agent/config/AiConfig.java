package dev.mvlcak.aster.agent.config;

import dev.mvlcak.aster.agent.tool.DiffTool;
import dev.mvlcak.aster.mcp.ResilientMcpToolCallbackProvider;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
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
	                             ResilientMcpToolCallbackProvider mcpToolCallbacks) {

		return ChatClient.builder(chatModel)
				.defaultSystem("""
						You are a helpful coding assistant named Aster. You have access to tools
						for reading files, searching code, running shell commands, and to tools
						exposed by connected MCP servers.
						
						Whenever you call a tool, your final message must report what that call
						actually did: name the tool, and state the concrete outcome from its result
						(what changed, which versions, which files, what failed). Never end your
						turn on a tool call without such a report, and never claim an outcome the
						tool result does not show.
						
						An MCP tool whose description carries an output schema answers with JSON in
						exactly that shape. Read the fields the schema names and answer from their
						values, rather than repeating the raw JSON or saying only that the tool ran.

						Current directory: %s
						""".formatted(System.getProperty("user.dir")))
				.defaultAdvisors(messageChatMemoryAdvisor)
				.defaultTools(grepTool, fileSystemTools, shellTools, diffTool, mcpToolCallbacks)
				.build();
	}

}
