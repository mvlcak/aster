package dev.mvlcak.aster.ai.config;

import dev.mvlcak.aster.ai.tool.DiffTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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
	public ChatClient chatClient(ChatModel chatModel, GrepTool grepTool, FileSystemTools fileSystemTools, ShellTools shellTools, MessageChatMemoryAdvisor messageChatMemoryAdvisor, DiffTool diffTool) {

		return ChatClient.builder(chatModel)
				.defaultSystem("""
						You are a helpful coding assistant named Aster. You have access to tools
						for reading files, searching code, running shell commands,
						
						Always give a summary in approximately 3 sentences what you did after prompt of client.
						Always after doing work(writing to files or creating files) use diff tool to show client what you have done.

						Current directory: %s
						""".formatted(System.getProperty("user.dir")))
				.defaultAdvisors(messageChatMemoryAdvisor)
				.defaultTools(grepTool, fileSystemTools, shellTools, diffTool)
				.build();
    }
	@Bean
	public ChatClient codeReviewClient(ChatModel chatModel) {
		return ChatClient
				.builder(chatModel)
				.defaultSystem(
						"""
								    Your task is to review code changes, whether they do what they should.
								    You should validate them against the given plan/task.
								    You must adhere to best practices.
								    Point out bugs or issues.
								
								    You are only given text describing the task and the code changes made -
								    you have no tools. Base your review entirely on that text.
								"""
				)
				.build();

	}

}
