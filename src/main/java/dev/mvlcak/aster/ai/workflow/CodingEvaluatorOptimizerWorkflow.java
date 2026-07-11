package dev.mvlcak.aster.ai.workflow;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CodingEvaluatorOptimizerWorkflow implements EvaluatorOptimizerWorkflow {

    private static final String CODE_REVIEW_PROMPT =
            """
                    You are a code reviewer for an agentic AI workflow evaluator optimizer
                    
                    Review the code and identify specific, actionable improvements: correctness bugs,
                    missed edge cases, and simplification opportunities. If previous suggestions and
                    evaluator feedback are provided below, revise your suggestions to address that
                    feedback directly instead of repeating what was already rejected.
                    
                    Respond with ONLY a JSON object mapping each suggestion's location
                    (e.g. "ClassName.java:methodName") to a concise description of the change to make.
                    Do not wrap the JSON in markdown or add commentary.
                    """;

    public static final String EVALUATE_PROPOSED_IMPROVEMENTS_PROMPT =
            """
                    You are a senior reviewer judging whether proposed code improvements fully
                    and correctly address the original task. Do not be lenient — only pass suggestions
                    that are correct, complete, and free of new issues.
                    
                    Respond with ONLY a JSON object containing exactly one entry:
                    - key: "PASS" if the suggestions fully address the task, otherwise "NEEDS_IMPROVEMENT"
                    - value: your reasoning, and if NEEDS_IMPROVEMENT, specific guidance on what to fix
                    
                    Do not wrap the JSON in markdown or add commentary.
                    """;
    private final ChatClient codeReviewClient;
    static final ParameterizedTypeReference<Map<String, String>> mapClass = new ParameterizedTypeReference<>() {
    };

    public CodingEvaluatorOptimizerWorkflow(ChatClient codeReviewClient) {
        this.codeReviewClient = codeReviewClient;
    }

    public Map<String, String> evaluate(String task) {
        return loop(task, new HashMap<>(), "", 1);
    }

    private Map<String, String> loop(String task, Map<String, String> latestSuggestions, String evaluation, int counter) {
        latestSuggestions = generate(task, latestSuggestions, evaluation);
        Map<String, String> evaluationResponse = evaluate(latestSuggestions, task);
        String outcome = evaluationResponse.keySet().iterator().next();
        evaluation = evaluationResponse.values().iterator().next();

        if ("PASS".equals(outcome) || counter > 2) {
            return latestSuggestions;
        }
        return loop(task, latestSuggestions, evaluation, counter + 1);
    }

    private Map<String, String> generate(String task, Map<String, String> previousSuggestions, String evaluation) {
        String request = CODE_REVIEW_PROMPT +
                "\n task: " + task +
                "\n previous suggestions: " + previousSuggestions +
                "\n evaluation on previous suggestions: " + evaluation;

        ChatClient.ChatClientRequestSpec requestSpec = codeReviewClient.prompt(request);
        ChatClient.CallResponseSpec responseSpec = requestSpec.call();
        Map<String, String> response = responseSpec.entity(mapClass);

        return response;
    }

    private Map<String, String> evaluate(Map<String, String> latestSuggestions, String task) {
        String request = EVALUATE_PROPOSED_IMPROVEMENTS_PROMPT +
                "\n task: " + task +
                "\n proposed suggestions: " + latestSuggestions;

        ChatClient.ChatClientRequestSpec requestSpec = codeReviewClient.prompt(request);
        ChatClient.CallResponseSpec responseSpec = requestSpec.call();
        Map<String, String> response = responseSpec.entity(mapClass);

        return response;
    }
}
