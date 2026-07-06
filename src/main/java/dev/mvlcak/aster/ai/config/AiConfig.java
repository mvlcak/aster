package dev.mvlcak.aster.ai.config;

import dev.mvlcak.aster.ai.routing.RoutingWorkflow;
import dev.mvlcak.aster.ai.tool.DiffTool;
import dev.mvlcak.aster.ai.workflow.DevelopmentWorkflow;
import dev.mvlcak.aster.chat.StreamingChatService;
import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.tui.AppState;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
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
						for reading files, searching code, running shell commands, searching for db tables,
						getting their schema and editing files. Use them to help the user with their codebase
						and help with creating sql queries.
						
						You are connected to Microsoft SQL Server db
						
						With findTables and getTAbleScript you are connected to database for which
						you should write queries. You search for table name and then get schema of table.
						Based on that create queries for user by writing that sql to file.
						You must always use this tool to get schema of tables you are connected with.
						
						When user asks about some file to edit it or write to it, scan all directories
						in workdir for this class and find it.

						Always give summary what you did after prompt of client.
						    Always after doing work(writing to files or creating files) use diff tool to show client what you have done.

						Current directory: %s
						""".formatted(System.getProperty("user.dir")))
				.defaultAdvisors(messageChatMemoryAdvisor)
				.defaultTools(grepTool, fileSystemTools, shellTools, diffTool)
				.build();
    }

	@Bean
	public ChatClient codeReviewClient(ChatModel chatModel, GrepTool grepTool, FileSystemTools fileSystemTools, ShellTools shellTools, DiffTool diffTool) {
		return ChatClient
				.builder(chatModel)
				.defaultSystem(
						"""
								Your task is to review code, whether it does what it should.
								You should validate it against plan.
								You must adhere to best practices.
								Point out bugs or issues.
								If you are working with compiled programming language like Java or Typescript
								use compiler to validate syntax
								"""
				)
				.defaultTools(grepTool, fileSystemTools, shellTools, diffTool)
				.build();

	}

	@Bean
	public StreamingChatService streamingChatService(@Qualifier("chatClient") ChatClient statelessChatClient,
	                                                 AppState appState, AppEventBus appEventBus,
	                                                 RoutingWorkflow routingWorkflow,
	                                                 DevelopmentWorkflow developmentWorkflow) {
		return new StreamingChatService(statelessChatClient, appState, appEventBus, routingWorkflow, developmentWorkflow);
	}

}
